/*
 * Copyright © 2017 Ocado (Ocava)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ocadotechnology.scenario;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.base.Preconditions;
import com.ocadotechnology.random.RepeatableRandom;
import com.ocadotechnology.validation.Failer;

@ExtendWith(ScenarioTestWrapper.class)
@DefaultStory
public abstract class AbstractStory {
    private static Logger logger;

    protected final StepsRunner stepsRunner;
    protected final ScenarioSimulationApi simulation;
    protected final StepCache stepCache = new StepCache();
    protected final AssertionCache assertionCache = new AssertionCache();
    protected final NotificationCache notificationCache = new NotificationCache();
    protected final StepManager stepManager;
    protected final ScenarioNotificationListener listener;

    public AbstractStory(AbstractScenarioSimulationApi simulation) {
        OcavaCleaner.register();

        this.simulation = simulation;
        stepsRunner = new StepsRunner(stepCache, simulation);

        listener = new ScenarioNotificationListener(notificationCache, stepsRunner);
        stepManager = new StepManager(stepCache, simulation, notificationCache, listener);

        Preconditions.checkArgument(this.getClass().isAnnotationPresent(Story.class),
                "Missing @Story annotation in %s", getClass().getSimpleName());
        RepeatableRandom.initialiseWithSeed(0);
        restartLogger();

        logger = LoggerFactory.getLogger(AbstractStory.class);
    }

    /**
     * Override if logging configuration is necessary.
     */
    public void restartLogger() {
    }

    @BeforeEach
    public final void setup() {
        Assumptions.assumeFalse(this.getClass().isAnnotationPresent(Disabled.class), "This test is @Disabled, so you cannot run it.");
        logger.info("START OF: {}", this.getClass().getSimpleName());
    }

    @AfterEach
    public final void tearDown() {
        Cleanable.cleanAll();
        closeAndFlushAllLoggers();
    }

    /**
     * Override if logging closing and flushing is necessary.
     */
    public void closeAndFlushAllLoggers() {
    }

    /**
     * Run via the {@link ScenarioTestWrapper}
     * Defaults to not requiring any special initialisation.
     */
    public void init() {
    }

    /**
     * Run via the {@link ScenarioTestWrapper}
     */
    public void executeTestSteps() {
        try {
            Assertions.assertTrue(stepCache.hasSteps(), "No steps in scenario test!");
            preprocessSteps(stepCache);

            execute();

            assertionCache.check();

            assertFinished();
        } catch (Throwable error) {
            //pass test if Fix Required is present
            logStepFailure(error);
            if (isFixRequired()) {
                return;
            }
            throw error;
        }
        Assertions.assertFalse(isFixRequired(), "Test is successful but it is annotated with FixRequired: " + getFixRequiredText());
    }

    boolean isFixRequired() {
        return this.getClass().isAnnotationPresent(FixRequired.class);
    }

    public void logStepFailure(Throwable error) {
        Executable unfinishedStep = stepsRunner.getUnfinishedStep();
        logger.info("Step failed: {}", unfinishedStep != null ? unfinishedStep : "", error);
    }

    /**
     * Override if you want to see the steps after the scenario is prepared but before it is executed.
     * This can be used to validate or modify the scenario before execution.
     */
    protected void preprocessSteps(StepCache stepCache) {
    }

    private void assertFinished() {
        if (!stepsRunner.isFinished()) {
            Executable unfinishedStep = stepsRunner.getUnfinishedStep();
            if (unfinishedStep instanceof NeverStep) {
                throw Failer.fail("Never step violated: " + unfinishedStep);
            }
            throw Failer.fail("Missing step: " + unfinishedStep);
        }
    }

    private String getFixRequiredText() {
        String fixRequiredValue = "";
        FixRequired fixRequired = this.getClass().getAnnotation(FixRequired.class);
        if (fixRequired != null) {
            fixRequiredValue = fixRequired.value();
        }
        return fixRequiredValue;
    }

    protected void execute() {
        try {
            while (!simulation.isStarted()) {
                stepsRunner.tryToExecuteNextStep(false);
            }
            stepCache.getFinalSteps().forEach(Executable::executeAndLog);
        } catch (Throwable exception) {
            if (stepsRunner.validateException(exception)) {
                stepsRunner.tryToExecuteNextStep(false); //Tidy up the now complete Exception step
            } else {
                throw exception;
            }
        }
    }
}

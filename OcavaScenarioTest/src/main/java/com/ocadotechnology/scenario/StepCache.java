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

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

import com.google.common.base.Preconditions;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.ocadotechnology.event.scheduling.Cancelable;

public class StepCache extends Cleanable {
    private static final Predicate<Throwable> EXCEPTION_CHECKER_DEFAULT = t -> false;
    private LinkedList<Executable> orderedSteps = new LinkedList<>();
    private Executable lastStep;
    private Multimap<String, Executable> unorderedSteps = LinkedHashMultimap.create();
    private final Set<String> allUnorderedStepNames = new HashSet<>();
    private int stepCounter = 0;
    private List<Executable> finalSteps = new ArrayList<>();
    private Predicate<Throwable> exceptionChecker = EXCEPTION_CHECKER_DEFAULT;

    public synchronized int getNextStepCounter() {
        return stepCounter++;
    }

    public synchronized void addCheckStep(ExceptionCheckStep testStep) {
        addOrdered(testStep);
        exceptionChecker = testStep::checkThrowable;
    }

    public Predicate<Throwable> getExceptionChecker() {
        return exceptionChecker;
    }

    public synchronized void addOrdered(Executable testStep) {
        validateExceptionAsLastStep(testStep);
        if (isMergeable(testStep)) {
            lastStep.merge(testStep);
        } else {
            orderedSteps.add(testStep);
        }
        lastStep = testStep;
    }

    public synchronized void addOrdered(int idx, Executable testStep) {
        validateExceptionAsLastStep(testStep);
        if (idx == orderedSteps.size()) {
            addOrdered(testStep);
        } else {
            orderedSteps.add(idx, testStep);
        }
    }

    private void validateExceptionAsLastStep(Executable testStep) {
        Preconditions.checkState(exceptionChecker == EXCEPTION_CHECKER_DEFAULT, "You can not add another steps after Exception step. Invalid step [%s] ", testStep);
    }

    public synchronized void clearOrderedSteps() {
        orderedSteps.clear();
        lastStep = null;
    }

    public synchronized void clearUnorderedSteps() {
        unorderedSteps.clear();
        allUnorderedStepNames.clear();
    }

    public synchronized ImmutableList<Executable> getOrderedStepsView() {
        return ImmutableList.copyOf(orderedSteps);
    }

    public synchronized void addFinalStep(Executable testStep) {
        finalSteps.add(testStep);
    }

    public List<Executable> getFinalSteps() {
        return finalSteps;
    }

    public synchronized Executable removeLastStep() {
        LinkedList<Executable> list = orderedSteps;
        Executable removedSteps = list.removeLast();
        lastStep = list.getLast();
        return removedSteps;
    }

    private synchronized boolean isMergeable(Executable testStep) {
        return testStep.isMergeable() && lastStep != null && lastStep.isMergeable();
    }

    public synchronized boolean isUnorderedStepFinished(String name) {
        return !unorderedSteps.containsKey(name);
    }

    public synchronized String getRandomUnorderedStepName() {
        String name = String.valueOf(System.nanoTime());
        while (unorderedSteps.containsKey(name)) {
            name = String.valueOf(System.nanoTime());
        }
        return name;
    }

    public synchronized void addUnordered(String name, Executable testStep) {
        allUnorderedStepNames.add(name);
        unorderedSteps.put(name, testStep);
    }

    public ImmutableSet<String> getAllUnorderedStepNames() {
        return ImmutableSet.copyOf(allUnorderedStepNames);
    }

    public boolean hasAddedStepWithName(String name) {
        return allUnorderedStepNames.contains(name);
    }

    public synchronized void removeAndCancel(String name) {
        Preconditions.checkState(unorderedSteps.containsKey(name),
                "Tried to remove unordered steps with name '%s', but didn't find one. unorderedSteps: %s", name, unorderedSteps);
        removeAndCancelIfPresent(name);
    }

    public synchronized void removeAndCancelIfPresent(String name) {
        unorderedSteps.removeAll(name).stream().filter(step -> step instanceof Cancelable).forEach(step -> ((Cancelable) step).cancel());
    }

    public synchronized Collection<Executable> getUnorderedSteps() {
        return unorderedSteps.values();
    }

    public synchronized boolean isFinished() {
        for (Executable unorderedStep : unorderedSteps.values()) {
            if (unorderedStep.isRequired() && !unorderedStep.isFinished()) {
                return false;
            }
        }
        return orderedSteps.isEmpty();
    }

    public synchronized Executable getUnfinishedUnorderedStep() {
        for (Executable unorderedStep : unorderedSteps.values()) {
            if (unorderedStep.isRequired() && !unorderedStep.isFinished()) {
                return unorderedStep;
            }
        }
        return null;
    }

    @Override
    public synchronized void clean() {
        orderedSteps = new LinkedList<>();
        unorderedSteps = LinkedHashMultimap.create();
        allUnorderedStepNames.clear();
        finalSteps = new ArrayList<>();
        stepCounter = 0;
        exceptionChecker = EXCEPTION_CHECKER_DEFAULT;
    }

    public synchronized Executable getNextStep() {
        return orderedSteps.poll();
    }

    public synchronized Executable peekNextStep() {
        return orderedSteps.peek();
    }

    public boolean hasSteps() {
        return !orderedSteps.isEmpty() || !unorderedSteps.isEmpty() || !finalSteps.isEmpty();
    }
}

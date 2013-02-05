/**
 * Copyright 2012-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.informant.testing.ui;

import checkers.igj.quals.Immutable;
import checkers.nullness.quals.Nullable;

/**
 * @author Trask Stalnaker
 * @since 0.5
 */
@Immutable
class NestableCall {

    @Nullable
    private final NestableCall child;
    private final int numExpensiveCalls;
    private final int maxTimeMillis;
    private final int maxHeadlineLength;

    NestableCall() {
        this(0, 0, 0);
    }

    NestableCall(int numExpensiveCalls, int maxTimeMillis, int maxHeadlineLength) {
        this.child = null;
        this.numExpensiveCalls = numExpensiveCalls;
        this.maxTimeMillis = maxTimeMillis;
        this.maxHeadlineLength = maxHeadlineLength;
    }

    NestableCall(NestableCall child, int numExpensiveCalls, int maxTimeMillis,
            int maxHeadlineLength) {

        this.child = child;
        this.numExpensiveCalls = numExpensiveCalls;
        this.maxTimeMillis = maxTimeMillis;
        this.maxHeadlineLength = maxHeadlineLength;
    }

    void execute() throws InterruptedException {
        if (child != null) {
            child.execute();
        }
        // the expensive calls are spread out over different line numbers (as opposed to using a
        // single loop) so that stack trace captures across the different expensive calls will not
        // be identical
        for (int i = 0; i < numExpensiveCalls; i++) {
            new ExpensiveCall(maxTimeMillis, maxHeadlineLength).execute();
            if (i < numExpensiveCalls) {
                new ExpensiveCall(maxTimeMillis, maxHeadlineLength).execute();
                i++;
            }
            if (i < numExpensiveCalls) {
                new ExpensiveCall(maxTimeMillis, maxHeadlineLength).execute();
                i++;
            }
            if (i < numExpensiveCalls) {
                new ExpensiveCall(maxTimeMillis, maxHeadlineLength).execute();
                i++;
            }
            if (i < numExpensiveCalls) {
                new ExpensiveCall(maxTimeMillis, maxHeadlineLength).execute();
                i++;
            }
            if (i < numExpensiveCalls) {
                new ExpensiveCall(maxTimeMillis, maxHeadlineLength).execute();
                i++;
            }
            if (i < numExpensiveCalls) {
                new ExpensiveCall(maxTimeMillis, maxHeadlineLength).execute();
                i++;
            }
            if (i < numExpensiveCalls) {
                new ExpensiveCall(maxTimeMillis, maxHeadlineLength).execute();
                i++;
            }
            if (i < numExpensiveCalls) {
                new ExpensiveCall(maxTimeMillis, maxHeadlineLength).execute();
                i++;
            }
            if (i < numExpensiveCalls) {
                new ExpensiveCall(maxTimeMillis, maxHeadlineLength).execute();
                i++;
            }
        }
    }
}

/**
 * Copyright 2012 the original author or authors.
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
package org.informantproject.core.weaving;

import javax.annotation.concurrent.ThreadSafe;

import org.informantproject.api.Timer;

/**
 * @author Trask Stalnaker
 * @since 0.5
 */
class NopWeavingMetric implements WeavingMetric {

    static final NopWeavingMetric INSTANCE = new NopWeavingMetric();

    public Timer start() {
        return NopTimer.INSTANCE;
    }

    @ThreadSafe
    private static class NopTimer implements Timer {
        private static final NopTimer INSTANCE = new NopTimer();
        public void end() {}
    }
}
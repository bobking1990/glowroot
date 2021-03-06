/*
 * Copyright 2011-2016 the original author or authors.
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
package org.glowroot.agent.tests;

import java.util.List;
import java.util.Set;

import com.google.common.collect.Sets;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import org.glowroot.agent.it.harness.AppUnderTest;
import org.glowroot.agent.it.harness.Container;
import org.glowroot.agent.it.harness.Containers;
import org.glowroot.agent.it.harness.TransactionMarker;
import org.glowroot.agent.tests.app.LevelOne;
import org.glowroot.agent.tests.app.LogCause;
import org.glowroot.agent.tests.app.LogError;
import org.glowroot.agent.tests.plugin.LogCauseAspect.LogCauseAdvice;
import org.glowroot.wire.api.model.AgentConfigOuterClass.AgentConfig.TransactionConfig;
import org.glowroot.wire.api.model.Proto;
import org.glowroot.wire.api.model.TraceOuterClass.Trace;

import static org.assertj.core.api.Assertions.assertThat;

public class ErrorCaptureIT {

    private static Container container;

    @BeforeClass
    public static void setUp() throws Exception {
        container = Containers.create();
    }

    @AfterClass
    public static void tearDown() throws Exception {
        container.close();
    }

    @After
    public void afterEachTest() throws Exception {
        container.checkAndReset();
    }

    @Test
    public void shouldCaptureError() throws Exception {
        // given
        container.getConfigService().updateTransactionConfig(
                TransactionConfig.newBuilder()
                        .setSlowThresholdMillis(ProtoOptional.of(10000))
                        .build());
        // when
        Trace trace = container.execute(ShouldCaptureError.class);
        // then
        Trace.Header header = trace.getHeader();
        assertThat(header.getEntryCount()).isEqualTo(3);
        List<Trace.Entry> entries = trace.getEntryList();
        assertThat(header.hasError()).isTrue();
        assertThat(header.getError().getMessage()).isEqualTo(RuntimeException.class.getName());
        assertThat(header.getError().hasException()).isTrue();
        assertThat(entries).hasSize(3);
        Trace.Entry entry1 = entries.get(0);
        assertThat(entry1.hasError()).isFalse();
        assertThat(entry1.getDepth()).isEqualTo(0);
        Trace.Entry entry2 = entries.get(1);
        assertThat(entry2.hasError()).isFalse();
        assertThat(entry2.getDepth()).isEqualTo(1);
        Trace.Entry entry3 = entries.get(2);
        assertThat(entry3.hasError()).isFalse();
        assertThat(entry3.getDepth()).isEqualTo(2);
    }

    @Test
    public void shouldCaptureErrorWithTraceEntryStackTrace() throws Exception {
        // given
        // when
        Trace trace = container.execute(ShouldCaptureErrorWithTraceEntryStackTrace.class);
        // then
        List<Trace.Entry> entries = trace.getEntryList();
        assertThat(trace.getHeader().hasError()).isFalse();
        assertThat(entries).hasSize(2);
        Trace.Entry entry1 = entries.get(0);
        assertThat(entry1.getDepth()).isEqualTo(0);
        Trace.Entry entry2 = entries.get(1);
        assertThat(entry2.getDepth()).isEqualTo(1);
        assertThat(entry2.getError().getMessage()).isNotEmpty();
        List<Proto.StackTraceElement> stackTraceElements =
                entry2.getLocationStackTraceElementList();
        assertThat(stackTraceElements.get(0).getClassName()).isEqualTo(LogError.class.getName());
        assertThat(stackTraceElements.get(0).getMethodName()).isEqualTo("addNestedErrorEntry");
        assertThat(stackTraceElements.get(0).getFileName()).isEqualTo("LogError.java");
    }

    @Test
    public void shouldCaptureErrorWithCausalChain() throws Exception {
        // given
        // when
        Trace trace = container.execute(ShouldCaptureErrorWithCausalChain.class);
        // then
        List<Trace.Entry> entries = trace.getEntryList();
        assertThat(trace.getHeader().hasError()).isFalse();
        assertThat(entries).hasSize(1);
        Trace.Entry entry = entries.get(0);
        assertThat(entry.getError().getMessage()).isNotEmpty();
        assertThat(entry.getMessage()).isEqualTo("ERROR -- abc");
        Proto.Throwable throwable = entry.getError().getException();
        assertThat(throwable.getClassName()).isEqualTo("java.lang.IllegalStateException");
        assertThat(throwable.getMessage()).isEqualTo("java.lang.IllegalArgumentException: Cause 3");
        assertThat(throwable.getStackTraceElementList().get(0).getClassName())
                .isEqualTo(LogCauseAdvice.class.getName());
        assertThat(throwable.getStackTraceElementList().get(0).getMethodName())
                .isEqualTo("onAfter");
        assertThat(throwable.getFramesInCommonWithEnclosing()).isZero();
        Proto.Throwable cause = throwable.getCause();
        assertThat(cause.getClassName()).isEqualTo("java.lang.IllegalArgumentException");
        assertThat(cause.getMessage()).isEqualTo("Cause 3");
        assertThat(cause.getStackTraceElementList().get(0).getClassName())
                .isEqualTo(LogCauseAdvice.class.getName());
        assertThat(cause.getStackTraceElementList().get(0).getMethodName()).isEqualTo("onAfter");
        assertThat(cause.getFramesInCommonWithEnclosing()).isGreaterThan(0);
        Set<Integer> causeLineNumbers = Sets.newHashSet();
        causeLineNumbers.add(cause.getStackTraceElementList().get(0).getLineNumber());
        cause = cause.getCause();
        assertThat(cause.getClassName()).isEqualTo("java.lang.IllegalStateException");
        assertThat(cause.getMessage()).isEqualTo("Cause 2");
        assertThat(cause.getStackTraceElementList().get(0).getClassName())
                .isEqualTo(LogCauseAdvice.class.getName());
        assertThat(cause.getStackTraceElementList().get(0).getMethodName()).isEqualTo("onAfter");
        assertThat(cause.getFramesInCommonWithEnclosing()).isGreaterThan(0);
        causeLineNumbers.add(cause.getStackTraceElementList().get(0).getLineNumber());
        cause = cause.getCause();
        assertThat(cause.getClassName()).isEqualTo("java.lang.NullPointerException");
        assertThat(cause.getMessage()).isEqualTo("Cause 1");
        assertThat(cause.getStackTraceElementList().get(0).getClassName())
                .isEqualTo(LogCauseAdvice.class.getName());
        assertThat(cause.getStackTraceElementList().get(0).getMethodName()).isEqualTo("onAfter");
        assertThat(cause.getFramesInCommonWithEnclosing()).isGreaterThan(0);
        causeLineNumbers.add(cause.getStackTraceElementList().get(0).getLineNumber());
        // make sure they are all different line numbers
        assertThat(causeLineNumbers).hasSize(3);
    }

    @Test
    public void shouldAddNestedErrorEntry() throws Exception {
        // given
        // when
        Trace trace = container.execute(ShouldAddNestedErrorEntry.class);
        // then
        List<Trace.Entry> entries = trace.getEntryList();
        assertThat(trace.getHeader().hasError()).isFalse();
        assertThat(entries).hasSize(2);
        Trace.Entry entry1 = entries.get(0);
        assertThat(entry1.getDepth()).isEqualTo(0);
        assertThat(entry1.getMessage()).isEqualTo("outer entry to test nesting level");
        Trace.Entry entry2 = entries.get(1);
        assertThat(entry2.getDepth()).isEqualTo(1);
        assertThat(entry2.getError().getMessage()).isEqualTo("test add nested error entry message");
    }

    public static class ShouldCaptureError implements AppUnderTest {
        @Override
        public void executeApp() throws Exception {
            RuntimeException expected = new RuntimeException();
            try {
                new LevelOne(expected).call("a", "b");
            } catch (RuntimeException e) {
                if (e != expected) {
                    // suppress expected exception
                    throw e;
                }
            }
        }
    }

    public static class ShouldCaptureErrorWithTraceEntryStackTrace
            implements AppUnderTest, TransactionMarker {
        @Override
        public void executeApp() throws Exception {
            transactionMarker();
        }
        @Override
        public void transactionMarker() throws Exception {
            new LogError().addNestedErrorEntry();
        }
    }

    public static class ShouldCaptureErrorWithCausalChain
            implements AppUnderTest, TransactionMarker {
        @Override
        public void executeApp() throws Exception {
            transactionMarker();
        }
        @Override
        public void transactionMarker() throws Exception {
            new LogCause().log("abc");
        }
    }

    public static class ShouldAddNestedErrorEntry implements AppUnderTest, TransactionMarker {
        @Override
        public void executeApp() throws Exception {
            transactionMarker();
        }
        @Override
        public void transactionMarker() throws Exception {
            new LogError().addNestedErrorEntry();
        }
    }
}

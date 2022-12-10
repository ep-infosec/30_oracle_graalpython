/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates.
 * Copyright (c) 2014, Regents of the University of California
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.graal.python.builtins.objects.traceback;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import static com.oracle.graal.python.builtins.objects.traceback.PTraceback.J_TB_FRAME;
import static com.oracle.graal.python.builtins.objects.traceback.PTraceback.J_TB_LASTI;
import static com.oracle.graal.python.builtins.objects.traceback.PTraceback.J_TB_LINENO;
import static com.oracle.graal.python.builtins.objects.traceback.PTraceback.J_TB_NEXT;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___DIR__;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.frame.PFrame;
import com.oracle.graal.python.builtins.objects.frame.PFrame.Reference;
import com.oracle.graal.python.builtins.objects.function.PArguments;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.bytecode.PBytecodeGeneratorRootNode;
import com.oracle.graal.python.nodes.bytecode.PBytecodeRootNode;
import com.oracle.graal.python.nodes.frame.MaterializeFrameNode;
import com.oracle.graal.python.nodes.frame.ReadCallerFrameNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.object.PythonObjectFactory;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleStackTraceElement;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.RootNode;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.LoopConditionProfile;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PTraceback)
public final class TracebackBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return TracebackBuiltinsFactory.getFactories();
    }

    @Builtin(name = J___DIR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class DirNode extends PythonBuiltinNode {
        @Specialization
        public Object dir(@SuppressWarnings("unused") PTraceback self) {
            return factory().createList(PTraceback.getTbFieldNames());
        }
    }

    /**
     * Use the Truffle stacktrace attached to an exception to populate the information in the
     * {@link PTraceback} and its tb_next chain as far as the stacktrace goes for this segment.
     *
     * @see GetTracebackNode
     */
    public abstract static class MaterializeTruffleStacktraceNode extends Node {
        public abstract void execute(PTraceback tb);

        @Specialization(guards = "tb.isMaterialized()")
        void doExisting(@SuppressWarnings("unused") PTraceback tb) {
        }

        @TruffleBoundary
        @Specialization(guards = "!tb.isMaterialized()")
        void doMaterialize(PTraceback tb,
                        @Cached MaterializeFrameNode materializeFrameNode,
                        @Cached GetTracebackNode getTracebackNode,
                        @Cached PythonObjectFactory factory) {
            /*
             * Truffle stacktrace consists of the frames captured during the unwinding and the
             * frames that are now on the Java stack. We don't want the frames from the stack to
             * creep in. They would be incorrect because we are no longer in the location where the
             * exception was caught, and unwanted because Python doesn't include frames from the
             * active stack in the traceback. Truffle doesn't tell us where the boundary between
             * exception frames and frames from the Java stack is, so we cut it off when we see the
             * current call target in the stacktrace.
             */
            PTraceback next = null;
            if (tb.getLazyTraceback().getNextChain() != null) {
                next = getTracebackNode.execute(tb.getLazyTraceback().getNextChain());
            }
            // The logic of skipping and cutting off frames here and in GetTracebackNode must be the
            // same
            PException pException = tb.getLazyTraceback().getException();
            boolean skipFirst = pException.shouldHideLocation();
            for (TruffleStackTraceElement element : pException.getTruffleStackTrace()) {
                if (pException.shouldCutOffTraceback(element)) {
                    break;
                }
                if (skipFirst) {
                    skipFirst = false;
                    continue;
                }
                if (LazyTraceback.elementWantedForTraceback(element)) {
                    /*
                     * Bytecode tracebacks pull location data from corresponding stacktrace element.
                     * AST tracebacks pull locations from the call node of the element "above".
                     */
                    if (LazyTraceback.elementWantedForTraceback(element)) {
                        PFrame pFrame = materializeFrame(element, materializeFrameNode);
                        next = factory.createTraceback(pFrame, pFrame.getLine(), next);
                    }
                }
            }
            if (tb.getLazyTraceback().catchingFrameWantedForTraceback()) {
                PBytecodeRootNode rootNode = (PBytecodeRootNode) pException.getCatchLocation();
                tb.setLineno(rootNode.bciToLine(pException.getCatchBci()));
                tb.setNext(next);
            } else {
                assert next != null;
                tb.setLineno(next.getLineno());
                tb.setFrame(next.getFrame());
                tb.setNext(next.getNext());
            }
            tb.markMaterialized(); // Marks the Truffle stacktrace part as materialized
        }

        private static PFrame materializeFrame(TruffleStackTraceElement element, MaterializeFrameNode materializeFrameNode) {
            Node location = element.getLocation();
            RootNode rootNode = element.getTarget().getRootNode();
            if (rootNode instanceof PBytecodeRootNode || rootNode instanceof PBytecodeGeneratorRootNode) {
                location = rootNode;
            }
            // create the PFrame and refresh frame values
            return materializeFrameNode.execute(null, location, false, true, element.getFrame());
        }
    }

    @Builtin(name = J_TB_FRAME, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetTracebackFrameNode extends PythonBuiltinNode {
        public abstract PFrame execute(VirtualFrame frame, Object traceback);

        public static GetTracebackFrameNode create() {
            return TracebackBuiltinsFactory.GetTracebackFrameNodeFactory.create(null);
        }

        @Specialization(guards = "hasPFrame(tb)")
        PFrame getExisting(PTraceback tb) {
            return tb.getFrame();
        }

        // case 1: not on stack: there is already a PFrame (so the frame of this frame info is
        // no
        // longer on the stack) and the frame has already been materialized
        @Specialization(guards = {"!hasPFrame(tb)", "hasFrameInfo(tb)", "isMaterialized(tb.getFrameInfo())", "hasVisibleFrame(tb)"})
        PFrame doMaterializedFrame(PTraceback tb) {
            Reference frameInfo = tb.getFrameInfo();
            assert frameInfo.isEscaped() : "cannot create traceback for non-escaped frame";
            PFrame escapedFrame = frameInfo.getPyFrame();
            assert escapedFrame != null;

            tb.setFrame(escapedFrame);
            return escapedFrame;
        }

        // case 2: on stack: the PFrame is not yet available so the frame must still be on the
        // stack
        @Specialization(guards = {"!hasPFrame(tb)", "hasFrameInfo(tb)", "!isMaterialized(tb.getFrameInfo())", "hasVisibleFrame(tb)"})
        PFrame doOnStack(VirtualFrame frame, PTraceback tb,
                        @Cached MaterializeFrameNode materializeNode,
                        @Cached ReadCallerFrameNode readCallerFrame,
                        @Cached ConditionProfile isCurFrameProfile) {
            Reference frameInfo = tb.getFrameInfo();
            assert frameInfo.isEscaped() : "cannot create traceback for non-escaped frame";

            PFrame escapedFrame;

            // case 2.1: the frame info refers to the current frame
            if (isCurFrameProfile.profile(PArguments.getCurrentFrameInfo(frame) == frameInfo)) {
                // materialize the current frame; marking is not necessary (already done);
                // refreshing
                // values is also not necessary (will be done on access to the locals or when
                // returning
                // from the frame)
                escapedFrame = materializeNode.execute(frame, false);
            } else {
                // case 2.2: the frame info does not refer to the current frame
                for (int i = 0;; i++) {
                    escapedFrame = readCallerFrame.executeWith(frame, i);
                    if (escapedFrame == null || escapedFrame.getRef() == frameInfo) {
                        break;
                    }
                }
            }

            assert escapedFrame != null : "Failed to find escaped frame on stack";
            tb.setFrame(escapedFrame);
            return escapedFrame;
        }

        // case 3: there is no PFrame[Ref], we need to take the top frame from the Truffle
        // stacktrace instead
        @Specialization(guards = "!hasVisibleFrame(tb)")
        PFrame doFromTruffle(PTraceback tb,
                        @Cached MaterializeTruffleStacktraceNode materializeTruffleStacktraceNode) {
            materializeTruffleStacktraceNode.execute(tb);
            return tb.getFrame();
        }

        protected static boolean hasPFrame(PTraceback tb) {
            return tb.getFrame() != null;
        }

        protected static boolean hasFrameInfo(PTraceback tb) {
            return tb.getFrameInfo() != null;
        }

        protected static boolean hasVisibleFrame(PTraceback tb) {
            return tb.getLazyTraceback() == null || tb.getLazyTraceback().catchingFrameWantedForTraceback();
        }

        protected static boolean isMaterialized(PFrame.Reference frameInfo) {
            return frameInfo.getPyFrame() != null;
        }
    }

    @Builtin(name = J_TB_NEXT, minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2, isGetter = true, isSetter = true)
    @GenerateNodeFactory
    public abstract static class GetTracebackNextNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isNoValue(none)")
        Object get(PTraceback self, @SuppressWarnings("unused") PNone none,
                        @Cached MaterializeTruffleStacktraceNode materializeTruffleStacktraceNode) {
            materializeTruffleStacktraceNode.execute(self);
            return (self.getNext() != null) ? self.getNext() : PNone.NONE;
        }

        @Specialization(guards = "!isNoValue(next)")
        Object set(PTraceback self, PTraceback next,
                        @Cached("createCountingProfile()") LoopConditionProfile loopProfile,
                        @Cached MaterializeTruffleStacktraceNode materializeTruffleStacktraceNode) {
            // Check for loops
            PTraceback tb = next;
            while (loopProfile.profile(tb != null)) {
                if (tb == self) {
                    throw raise(ValueError, ErrorMessages.TRACEBACK_LOOP_DETECTED);
                }
                tb = tb.getNext();
            }
            // Realize whatever was in the truffle stacktrace, so that we don't overwrite the
            // user-set next later
            materializeTruffleStacktraceNode.execute(self);
            self.setNext(next);
            return PNone.NONE;
        }

        @Specialization(guards = "isNone(next)")
        Object clear(PTraceback self, @SuppressWarnings("unused") PNone next,
                        @Cached MaterializeTruffleStacktraceNode materializeTruffleStacktraceNode) {
            // Realize whatever was in the truffle stacktrace, so that we don't overwrite the
            // user-set next later
            materializeTruffleStacktraceNode.execute(self);
            self.setNext(null);
            return PNone.NONE;
        }

        @Specialization(guards = {"!isPNone(next)", "!isPTraceback(next)"})
        Object setError(@SuppressWarnings("unused") PTraceback self, Object next) {
            throw raise(TypeError, ErrorMessages.EXPECTED_TRACEBACK_OBJ, next);
        }
    }

    @Builtin(name = J_TB_LASTI, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetTracebackLastINode extends PythonBuiltinNode {
        @Specialization
        Object get(PTraceback self) {
            return self.getLasti();
        }
    }

    @Builtin(name = J_TB_LINENO, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    public abstract static class GetTracebackLinenoNode extends PythonBuiltinNode {
        @Specialization
        Object get(PTraceback self,
                        @Cached MaterializeTruffleStacktraceNode materializeTruffleStacktraceNode) {
            materializeTruffleStacktraceNode.execute(self);
            return self.getLineno();
        }
    }
}

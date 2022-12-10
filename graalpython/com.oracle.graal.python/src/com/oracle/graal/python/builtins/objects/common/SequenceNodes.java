/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.graal.python.builtins.objects.common;

import static com.oracle.graal.python.nodes.ErrorMessages.IS_NOT_A_SEQUENCE;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;

import com.oracle.graal.python.builtins.objects.str.PString;
import com.oracle.graal.python.builtins.objects.str.StringNodes;
import com.oracle.graal.python.lib.PySequenceCheckNode;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PNodeWithRaise;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.nodes.Node;

public abstract class SequenceNodes {

    @GenerateUncached
    @ImportStatic(PGuards.class)
    public abstract static class LenNode extends Node {

        public abstract int execute(PSequence seq);

        @Specialization
        int doPString(PString str,
                        @Cached StringNodes.StringLenNode lenNode) {
            return lenNode.execute(str);
        }

        @Specialization(guards = "!isPString(seq)")
        int doWithStorage(PSequence seq,
                        @Cached SequenceNodes.GetSequenceStorageNode getStorage) {
            return getStorage.execute(seq).length();
        }

        public static LenNode create() {
            return SequenceNodesFactory.LenNodeGen.create();
        }

        public static LenNode getUncached() {
            return SequenceNodesFactory.LenNodeGen.getUncached();
        }
    }

    @GenerateUncached
    public abstract static class GetSequenceStorageNode extends Node {

        public abstract SequenceStorage execute(Object seq);

        @Specialization(guards = {"seq.getClass() == cachedClass"})
        static SequenceStorage doSequenceCached(PSequence seq,
                        @Cached("seq.getClass()") Class<? extends PSequence> cachedClass) {
            return CompilerDirectives.castExact(seq, cachedClass).getSequenceStorage();
        }

        @Specialization(replaces = "doSequenceCached")
        static SequenceStorage doSequence(PSequence seq) {
            return seq.getSequenceStorage();
        }

        @Specialization(guards = "!isPSequence(seq)")
        static SequenceStorage doFallback(@SuppressWarnings("unused") Object seq) {
            CompilerDirectives.transferToInterpreterAndInvalidate();
            throw new IllegalStateException("cannot get sequence storage of non-sequence object");
        }

        static boolean isPSequence(Object object) {
            return object instanceof PSequence;
        }

        public static GetSequenceStorageNode create() {
            return SequenceNodesFactory.GetSequenceStorageNodeGen.create();
        }

        public static GetSequenceStorageNode getUncached() {
            return SequenceNodesFactory.GetSequenceStorageNodeGen.getUncached();
        }
    }

    @GenerateUncached
    public abstract static class GetObjectArrayNode extends Node {

        public abstract Object[] execute(Object seq);

        @Specialization
        static Object[] doGeneric(Object seq,
                        @Cached GetSequenceStorageNode getSequenceStorageNode,
                        @Cached SequenceStorageNodes.ToArrayNode toArrayNode) {
            return toArrayNode.execute(getSequenceStorageNode.execute(seq));
        }
    }

    @GenerateUncached
    public abstract static class SetSequenceStorageNode extends Node {

        public abstract void execute(PSequence s, SequenceStorage storage);

        @Specialization(guards = "s.getClass() == cachedClass")
        static void doSpecial(PSequence s, SequenceStorage storage,
                        @Cached("s.getClass()") Class<? extends PSequence> cachedClass) {
            cachedClass.cast(s).setSequenceStorage(storage);
        }

        @Specialization(replaces = "doSpecial")
        @CompilerDirectives.TruffleBoundary
        static void doGeneric(PSequence s, SequenceStorage storage) {
            s.setSequenceStorage(storage);
        }
    }

    public abstract static class CheckIsSequenceNode extends PNodeWithRaise {

        public abstract void execute(Object seq);

        @Specialization
        void check(Object obj,
                        @Cached PySequenceCheckNode sequenceCheckNode) {
            if (!sequenceCheckNode.execute(obj)) {
                throw raise(TypeError, IS_NOT_A_SEQUENCE, obj);
            }
        }
    }
}

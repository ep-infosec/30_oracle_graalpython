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
package com.oracle.graal.python.builtins.objects.reversed;

import static com.oracle.graal.python.nodes.ErrorMessages.OBJ_HAS_NO_LEN;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___ITER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___LENGTH_HINT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___NEXT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REDUCE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___SETSTATE__;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes;
import com.oracle.graal.python.builtins.objects.iterator.PBuiltinIterator;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.lib.PyObjectSizeNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallBinaryNode;
import com.oracle.graal.python.nodes.call.special.LookupAndCallUnaryNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PReverseIterator)
public class ReversedBuiltins extends PythonBuiltins {

    /*
     * "extendClasses" only needs one of the set of Java classes that are mapped to the Python
     * class.
     */

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ReversedBuiltinsFactory.getFactories();
    }

    @Builtin(name = J___NEXT__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class NextNode extends PythonUnaryBuiltinNode {

        @Specialization(guards = "self.isExhausted()")
        public Object exhausted(@SuppressWarnings("unused") PBuiltinIterator self) {
            throw raiseStopIteration();
        }

        @Specialization(guards = "!self.isExhausted()")
        Object next(VirtualFrame frame, PSequenceReverseIterator self,
                        @Cached("create(GetItem)") LookupAndCallBinaryNode callGetItem,
                        @Cached IsBuiltinClassProfile profile) {
            if (self.index >= 0) {
                try {
                    return callGetItem.executeObject(frame, self.getObject(), self.index--);
                } catch (PException e) {
                    e.expectIndexError(profile);
                }
            }
            self.setExhausted();
            throw raiseStopIteration();
        }

        @Specialization(guards = "!self.isExhausted()")
        Object next(PStringReverseIterator self,
                        @Cached TruffleString.SubstringNode substringNode) {
            if (self.index >= 0) {
                return substringNode.execute(self.value, self.index--, 1, TS_ENCODING, false);
            }
            self.setExhausted();
            throw raiseStopIteration();
        }
    }

    @Builtin(name = J___ITER__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class IterNode extends PythonUnaryBuiltinNode {

        @Specialization
        static Object doGeneric(Object self) {
            return self;
        }
    }

    @Builtin(name = J___LENGTH_HINT__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class LengthHintNode extends PythonUnaryBuiltinNode {

        @Specialization(guards = "self.isExhausted()")
        public int exhausted(@SuppressWarnings("unused") PBuiltinIterator self) {
            return 0;
        }

        @Specialization(guards = "!self.isExhausted()")
        public int lengthHint(PStringReverseIterator self) {
            return self.index + 1;
        }

        @Specialization(guards = {"!self.isExhausted()", "self.isPSequence()"})
        public int lengthHint(PSequenceReverseIterator self,
                        @Cached SequenceNodes.LenNode lenNode) {
            int len = lenNode.execute(self.getPSequence());
            if (len == -1) {
                throw raise(TypeError, OBJ_HAS_NO_LEN, self);
            }
            if (len < self.index) {
                return 0;
            }
            return self.index + 1;
        }

        @Specialization(guards = {"!self.isExhausted()", "!self.isPSequence()"})
        public int lengthHint(VirtualFrame frame, PSequenceReverseIterator self,
                        @Cached PyObjectSizeNode sizeNode) {
            int len = sizeNode.execute(frame, self.getObject());
            if (len < self.index) {
                return 0;
            }
            return self.index + 1;
        }
    }

    @Builtin(name = J___REDUCE__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReduceNode extends PythonUnaryBuiltinNode {

        @Specialization
        public Object reduce(PStringReverseIterator self,
                        @Shared("getClassNode") @Cached GetClassNode getClassNode) {
            if (self.isExhausted()) {
                return reduceInternal(self, "", null, getClassNode);
            }
            return reduceInternal(self, self.value, self.index, getClassNode);
        }

        @Specialization(guards = "self.isPSequence()")
        public Object reduce(PSequenceReverseIterator self,
                        @Shared("getClassNode") @Cached GetClassNode getClassNode) {
            if (self.isExhausted()) {
                return reduceInternal(self, factory().createList(), null, getClassNode);
            }
            return reduceInternal(self, self.getPSequence(), self.index, getClassNode);
        }

        @Specialization(guards = "!self.isPSequence()")
        public Object reduce(VirtualFrame frame, PSequenceReverseIterator self,
                        @Cached("create(T___REDUCE__)") LookupAndCallUnaryNode callReduce,
                        @Shared("getClassNode") @Cached GetClassNode getClassNode) {
            Object content = callReduce.executeObject(frame, self.getPSequence());
            return reduceInternal(self, content, self.index, getClassNode);
        }

        private PTuple reduceInternal(Object self, Object arg, Object state, GetClassNode getClassNode) {
            Object revIter = getClassNode.execute(self);
            PTuple args = factory().createTuple(new Object[]{arg});
            // callable, args, state (optional)
            if (state != null) {
                return factory().createTuple(new Object[]{revIter, args, state});
            } else {
                return factory().createTuple(new Object[]{revIter, args});
            }
        }
    }

    @Builtin(name = J___SETSTATE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class SetStateNode extends PythonBinaryBuiltinNode {
        @Specialization
        public static Object setState(VirtualFrame frame, PBuiltinIterator self, Object index,
                        @Cached PyNumberAsSizeNode asSizeNode) {
            int idx = asSizeNode.executeExact(frame, index);
            if (idx < -1) {
                idx = -1;
            }
            self.index = idx;
            return PNone.NONE;
        }
    }
}

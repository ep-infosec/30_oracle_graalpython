/*
 * Copyright (c) 2021, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.itertools;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import static com.oracle.graal.python.nodes.ErrorMessages.INVALID_ARGS;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___ITER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___NEXT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REDUCE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___SETSTATE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.T___SETSTATE__;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.BuiltinFunctions;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.lib.PyObjectGetIter;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaIntLossyNode;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.LoopConditionProfile;

@CoreFunctions(extendClasses = {PythonBuiltinClassType.PIslice})
public final class IsliceBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return IsliceBuiltinsFactory.getFactories();
    }

    @Builtin(name = J___ITER__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class IterNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object iter(PIslice self) {
            return self;
        }
    }

    @Builtin(name = J___NEXT__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class NextNode extends PythonUnaryBuiltinNode {
        @Specialization(guards = "isNone(self.getIterable())")
        Object next(@SuppressWarnings("unused") PIslice self) {
            throw raiseStopIteration();
        }

        @Specialization(guards = "!isNone(self.getIterable())")
        Object next(VirtualFrame frame, PIslice self,
                        @Cached BuiltinFunctions.NextNode nextNode,
                        @Cached LoopConditionProfile loopProfile,
                        @Cached BranchProfile nextExceptionProfile,
                        @Cached BranchProfile nextExceptionProfile2,
                        @Cached BranchProfile setNextProfile) {
            Object it = self.getIterable();
            int stop = self.getStop();
            Object item;
            while (loopProfile.profile(self.getCnt() < self.getNext())) {
                try {
                    item = nextNode.execute(frame, it, PNone.NO_VALUE);
                } catch (PException e) {
                    nextExceptionProfile.enter();
                    // C code uses any exception to clear the iterator
                    self.setIterable(PNone.NONE);
                    throw e;
                }
                self.setCnt(self.getCnt() + 1);
            }
            if (stop != -1 && self.getCnt() >= stop) {
                self.setIterable(PNone.NONE);
                throw raiseStopIteration();
            }
            try {
                item = nextNode.execute(frame, it, PNone.NO_VALUE);
            } catch (PException e) {
                nextExceptionProfile2.enter();
                self.setIterable(PNone.NONE);
                throw e;
            }
            self.setCnt(self.getCnt() + 1);
            int oldNext = self.getNext();
            self.setNext(self.getNext() + self.getStep());
            if (self.getNext() < oldNext || (stop != -1 && self.getNext() > stop)) {
                setNextProfile.enter();
                self.setNext(stop);
            }
            return item;
        }
    }

    @Builtin(name = J___REDUCE__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReduceNode extends PythonUnaryBuiltinNode {
        @Specialization(guards = "isNone(self.getIterable())")
        Object reduceNoIterable(VirtualFrame frame, PIslice self,
                        @Cached GetClassNode getClassNode,
                        @Cached PyObjectGetIter getIter) {
            // return type(self), (iter([]), 0), 0
            Object type = getClassNode.execute(self);
            PTuple tuple = factory().createTuple(new Object[]{getIter.execute(frame, factory().createList()), 0});
            return factory().createTuple(new Object[]{type, tuple, 0});
        }

        @Specialization(guards = "!isNone(self.getIterable())")
        Object reduce(PIslice self,
                        @Cached GetClassNode getClassNode) {
            Object type = getClassNode.execute(self);
            Object stop = (self.getStop() == -1) ? PNone.NONE : self.getStop();
            PTuple tuple = factory().createTuple(new Object[]{self.getIterable(), self.getNext(), stop, self.getStep()});
            return factory().createTuple(new Object[]{type, tuple, self.getCnt()});
        }
    }

    @Builtin(name = J___SETSTATE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class SetStateNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object setState(PIslice self, Object state,
                        @Cached CastToJavaIntLossyNode castInt) {
            try {
                self.setCnt(castInt.execute(state));
            } catch (CannotCastException e) {
                throw raise(ValueError, INVALID_ARGS, T___SETSTATE__);
            }
            return PNone.NONE;
        }
    }

}

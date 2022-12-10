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

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.StopIteration;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___ITER__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___NEXT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REDUCE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___SETSTATE__;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.BuiltinFunctions;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.object.GetClassNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.profiles.LoopConditionProfile;

@CoreFunctions(extendClasses = {PythonBuiltinClassType.PZipLongest})
public final class ZipLongestBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ZipLongestBuiltinsFactory.getFactories();
    }

    @Builtin(name = J___ITER__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class IterNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object iter(PZipLongest self) {
            return self;
        }
    }

    @Builtin(name = J___NEXT__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class NextNode extends PythonUnaryBuiltinNode {
        @SuppressWarnings("unused")
        @Specialization(guards = "zeroSize(self)")
        Object nextNoFillValue(VirtualFrame frame, PZipLongest self) {
            throw raiseStopIteration();
        }

        @Specialization(guards = {"!zeroSize(self)", "isNullFillValue(self)"})
        Object nextNoFillValue(VirtualFrame frame, PZipLongest self,
                        @Cached BuiltinFunctions.NextNode nextNode,
                        @Cached IsBuiltinClassProfile isStopIterationProfile,
                        @Cached ConditionProfile noItProfile,
                        @Cached ConditionProfile noActiveProfile,
                        @Cached LoopConditionProfile loopProfile) {
            return next(frame, self, PNone.NONE, nextNode, isStopIterationProfile, loopProfile, noItProfile, noActiveProfile);
        }

        @Specialization(guards = {"!zeroSize(self)", "!isNullFillValue(self)"})
        Object next(VirtualFrame frame, PZipLongest self,
                        @Cached BuiltinFunctions.NextNode nextNode,
                        @Cached IsBuiltinClassProfile isStopIterationProfile,
                        @Cached ConditionProfile noItProfile,
                        @Cached ConditionProfile noActiveProfile,
                        @Cached LoopConditionProfile loopProfile) {
            return next(frame, self, self.getFillValue(), nextNode, isStopIterationProfile, loopProfile, noItProfile, noActiveProfile);
        }

        private Object next(VirtualFrame frame, PZipLongest self, Object fillValue, BuiltinFunctions.NextNode nextNode, IsBuiltinClassProfile isStopIterationProfile, LoopConditionProfile loopProfile,
                        ConditionProfile noItProfile, ConditionProfile noActiveProfile) {
            Object[] result = new Object[self.getItTuple().length];
            loopProfile.profileCounted(result.length);
            for (int i = 0; loopProfile.inject(i < result.length); i++) {
                Object it = self.getItTuple()[i];
                Object item;
                if (noItProfile.profile(it == PNone.NONE)) {
                    item = fillValue;
                } else {
                    try {
                        item = nextNode.execute(frame, it, PNone.NO_VALUE);
                    } catch (PException e) {
                        if (isStopIterationProfile.profileException(e, StopIteration)) {
                            self.setNumActive(self.getNumActive() - 1);
                            if (noActiveProfile.profile(self.getNumActive() == 0)) {
                                throw raiseStopIteration();
                            } else {
                                item = fillValue;
                                self.getItTuple()[i] = PNone.NONE;
                            }
                        } else {
                            self.setNumActive(0);
                            throw e;
                        }
                    }
                }
                result[i] = item;
            }
            return factory().createTuple(result);
        }

        protected boolean isNullFillValue(PZipLongest self) {
            return self.getFillValue() == null;
        }

        protected boolean zeroSize(PZipLongest self) {
            return self.getItTuple().length == 0 || self.getNumActive() == 0;
        }
    }

    @Builtin(name = J___REDUCE__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class ReduceNode extends PythonUnaryBuiltinNode {
        @Specialization(guards = "isNullFillValue(self)")
        Object reduceNoFillValue(PZipLongest self,
                        @Cached GetClassNode getClass,
                        @Cached LoopConditionProfile loopProfile,
                        @Cached ConditionProfile noItProfile) {
            return reduce(getClass, self, PNone.NONE, loopProfile, noItProfile);
        }

        @Specialization(guards = "!isNullFillValue(self)")
        Object reducePos(PZipLongest self,
                        @Cached GetClassNode getClass,
                        @Cached LoopConditionProfile loopProfile,
                        @Cached ConditionProfile noItProfile) {
            return reduce(getClass, self, self.getFillValue(), loopProfile, noItProfile);
        }

        private Object reduce(GetClassNode getClass, PZipLongest self, Object fillValue, LoopConditionProfile loopProfile, ConditionProfile noItProfile) {
            Object type = getClass.execute(self);
            Object[] its = new Object[self.getItTuple().length];
            loopProfile.profileCounted(its.length);
            for (int i = 0; loopProfile.profile(i < its.length); i++) {
                Object it = self.getItTuple()[i];
                if (noItProfile.profile(it == PNone.NONE)) {
                    its[i] = factory().createEmptyTuple();
                } else {
                    its[i] = it;
                }
            }
            PTuple tuple = factory().createTuple(its);
            return factory().createTuple(new Object[]{type, tuple, fillValue});
        }

        protected boolean isNullFillValue(PZipLongest self) {
            return self.getFillValue() == null;
        }
    }

    @Builtin(name = J___SETSTATE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class SetStateNode extends PythonBinaryBuiltinNode {
        @Specialization
        static Object setState(PZipLongest self, Object state) {
            self.setFillValue(state);
            return PNone.NONE;
        }
    }

}

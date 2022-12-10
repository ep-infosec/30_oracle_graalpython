/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python.builtins.objects.contextvars;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.LookupError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___CLASS_GETITEM__;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;

@CoreFunctions(extendClasses = PythonBuiltinClassType.ContextVar)
public final class ContextVarBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ContextVarBuiltinsFactory.getFactories();
    }

    @Builtin(name = "get", minNumOfPositionalArgs = 1, maxNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class GetNode extends PythonBinaryBuiltinNode {
        @Specialization(guards = "isNoValue(def)")
        Object get(PContextVar self, @SuppressWarnings("unused") PNone def) {
            return get(self, PContextVar.NO_DEFAULT);
        }

        @Specialization(guards = "!isNoValue(def)")
        Object get(PContextVar self, Object def) {
            PythonContext.PythonThreadState threadState = getContext().getThreadState(getLanguage());
            Object value = self.get(threadState, def);
            if (value != null) {
                return value;
            }
            throw raise(LookupError);
        }
    }

    @Builtin(name = "set", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class SetNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object set(PContextVar self, Object value) {
            PythonContext.PythonThreadState threadState = getContext().getThreadState(getLanguage());
            Object oldValue = self.getValue(threadState);
            self.setValue(threadState, value);
            return factory().createContextVarsToken(self, oldValue);
        }
    }

    @Builtin(name = "reset", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class ResetNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object reset(PContextVar self, PContextVarsToken token,
                        @Cached PRaiseNode raise) {
            if (self == token.getVar()) {
                token.use(raise);
                PythonContext.PythonThreadState threadState = getContext().getThreadState(getLanguage());
                if (token.getOldValue() == null) {
                    PContextVarsContext context = threadState.getContextVarsContext();
                    context.contextVarValues = context.contextVarValues.without(self, self.getHash());
                } else {
                    self.setValue(threadState, token.getOldValue());
                }
            } else {
                throw raise.raise(ValueError, ErrorMessages.TOKEN_FOR_DIFFERENT_CONTEXTVAR, token);
            }
            return PNone.NONE;
        }

        @Specialization
        Object doError(@SuppressWarnings("unused") PContextVar self, Object token,
                        @Cached PRaiseNode raise) {
            throw raise.raise(TypeError, ErrorMessages.INSTANCE_OF_TOKEN_EXPECTED, token);
        }
    }

    @Builtin(name = J___CLASS_GETITEM__, minNumOfPositionalArgs = 2, isClassmethod = true)
    @GenerateNodeFactory
    public abstract static class ClassGetItemNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object classGetItem(Object cls, Object key) {
            return factory().createGenericAlias(cls, key);
        }
    }
}

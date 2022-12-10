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

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.function.PKeyword;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.call.CallNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;

@CoreFunctions(extendClasses = PythonBuiltinClassType.ContextVarsContext)
public class ContextBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return ContextBuiltinsFactory.getFactories();
    }

    @Builtin(name = "__getitem__", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class GetContextVar extends PythonBinaryBuiltinNode {
        @Specialization
        Object get(PContextVarsContext self, Object key,
                        @Cached PRaiseNode raise) {
            return getContextVar(self, key, null, raise);
        }
    }

    @Builtin(name = "run", takesVarArgs = true, takesVarKeywordArgs = true, minNumOfPositionalArgs = 2, parameterNames = {"$self", "$callable"})
    @GenerateNodeFactory
    public abstract static class Run extends PythonBuiltinNode {
        @Specialization
        Object get(VirtualFrame frame, PContextVarsContext self, Object fun, Object[] args, PKeyword[] keywords,
                        @Cached CallNode call,
                        @Cached PRaiseNode raise) {
            PythonContext.PythonThreadState threadState = getContext().getThreadState(getLanguage());
            self.enter(threadState, raise);
            try {
                return call.execute(frame, fun, args, keywords);
            } finally {
                self.leave(threadState);
            }
        }
    }

    @Builtin(name = "copy", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class Copy extends PythonUnaryBuiltinNode {
        @Specialization
        Object doCopy(PContextVarsContext self) {
            PContextVarsContext ret = factory().createContextVarsContext();
            ret.contextVarValues = self.contextVarValues;
            return ret;
        }
    }

    @Builtin(name = "get", minNumOfPositionalArgs = 2, maxNumOfPositionalArgs = 3)
    @GenerateNodeFactory
    public abstract static class GetMethod extends PythonBuiltinNode {
        @Specialization(guards = "isNoValue(def)")
        Object doGet(PContextVarsContext self, Object key, @SuppressWarnings("unused") Object def,
                        @Cached PRaiseNode raise) {
            return doGetDefault(self, key, PNone.NONE, raise);
        }

        @Specialization(guards = "!isNoValue(def)")
        Object doGetDefault(PContextVarsContext self, Object key, Object def,
                        @Cached PRaiseNode raise) {
            return getContextVar(self, key, def, raise);
        }

    }

    @Builtin(name = "__contains__", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    public abstract static class Contains extends PythonBuiltinNode {
        @Specialization
        boolean doIn(PContextVarsContext self, Object key,
                        @Cached PRaiseNode raise) {
            if (key instanceof PContextVar) {
                PContextVar var = (PContextVar) key;
                return self.contextVarValues.lookup(var, var.getHash()) != null;
            }
            throw raise.raise(PythonBuiltinClassType.TypeError, ErrorMessages.CONTEXTVAR_KEY_EXPECTED, key);
        }
    }

    private static Object getContextVar(PContextVarsContext self, Object key, Object def, PRaiseNode raise) {
        if (key instanceof PContextVar) {
            PContextVar ctxVar = (PContextVar) key;
            Object value = self.contextVarValues.lookup(key, ctxVar.getHash());
            if (value == null) {
                if (def == null) {
                    throw raise.raise(PythonBuiltinClassType.KeyError, new Object[]{key});
                } else {
                    return def;
                }
            } else {
                return value;
            }
        } else {
            throw raise.raise(PythonBuiltinClassType.TypeError, ErrorMessages.CONTEXTVAR_KEY_EXPECTED, key);
        }
    }
}

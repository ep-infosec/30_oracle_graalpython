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
package com.oracle.graal.python.builtins.modules;

import static com.oracle.graal.python.nodes.SpecialAttributeNames.T___FILE__;

import java.io.IOException;
import java.util.List;

import com.oracle.graal.python.PythonLanguage;
import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.ArgumentClinic.ClinicConversion;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.GraalHPyUniversalModuleBuiltinsClinicProviders.HPyUniversalLoadNodeClinicProviderGen;
import com.oracle.graal.python.builtins.objects.cext.common.LoadCExtException.ApiInitException;
import com.oracle.graal.python.builtins.objects.cext.common.LoadCExtException.ImportException;
import com.oracle.graal.python.builtins.objects.cext.hpy.GraalHPyContext;
import com.oracle.graal.python.builtins.objects.cext.hpy.HPyExternalFunctionNodes.HPyCheckHandleResultNode;
import com.oracle.graal.python.builtins.objects.module.PythonModule;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.runtime.ExecutionContext.IndirectCallContext;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(defineModule = GraalHPyUniversalModuleBuiltins.J_HPY_UNIVERSAL)
@GenerateNodeFactory
public final class GraalHPyUniversalModuleBuiltins extends PythonBuiltins {

    public static final String J_HPY_UNIVERSAL = "_hpy_universal";

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return GraalHPyUniversalModuleBuiltinsFactory.getFactories();
    }

    @Builtin(name = "load", parameterNames = {"name", "path", "debug"}, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    @ArgumentClinic(name = "name", conversion = ClinicConversion.TString)
    @ArgumentClinic(name = "path", conversion = ClinicConversion.TString)
    @ArgumentClinic(name = "debug", conversion = ClinicConversion.Boolean, defaultValue = "false")
    abstract static class HPyUniversalLoadNode extends PythonTernaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return HPyUniversalLoadNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        PythonModule doGeneric(VirtualFrame frame, TruffleString name, TruffleString path, boolean debug,
                        @Cached HPyCheckHandleResultNode checkHandleResultNode,
                        @Cached TruffleString.EqualNode eqNode) {
            PythonContext context = getContext();
            PythonLanguage language = getLanguage();
            Object state = IndirectCallContext.enter(frame, language, context, this);
            try {
                return loadHPyModule(context, name, path, debug, checkHandleResultNode);
            } catch (ApiInitException ie) {
                throw ie.reraise(getConstructAndRaiseNode(), frame);
            } catch (ImportException ie) {
                throw ie.reraise(getConstructAndRaiseNode(), frame);
            } catch (IOException e) {
                throw getConstructAndRaiseNode().raiseOSError(frame, e, eqNode);
            } finally {
                IndirectCallContext.exit(frame, language, context, state);
            }
        }

        @TruffleBoundary
        private PythonModule loadHPyModule(PythonContext context, TruffleString name, TruffleString path, boolean debug,
                        HPyCheckHandleResultNode checkHandleResultNode) throws IOException, ApiInitException, ImportException {
            Object result = GraalHPyContext.loadHPyModule(this, context, name, path, debug, checkHandleResultNode);
            if (!(result instanceof PythonModule)) {
                /*
                 * HPy does currently not support multi-phase extension module initialization. This
                 * means there is no structure defined in HPy that we could parse. Hence, whenever
                 * we reach this point, it's an error.
                 */
                throw PRaiseNode.raiseUncached(this, PythonErrorType.SystemError, ErrorMessages.INIT_S_RETURNED_AN_UNEXPECTED_VALUE, name);
            }
            PythonModule module = (PythonModule) result;
            module.setAttribute(T___FILE__, path);
            return module;
        }
    }
}

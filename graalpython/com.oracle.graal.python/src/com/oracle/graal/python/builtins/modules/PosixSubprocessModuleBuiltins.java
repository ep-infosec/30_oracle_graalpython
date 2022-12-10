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
package com.oracle.graal.python.builtins.modules;

import static com.oracle.graal.python.runtime.exception.PythonErrorType.RuntimeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;

import java.util.Arrays;
import java.util.List;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.annotations.ArgumentClinic.ClinicConversion;
import com.oracle.graal.python.annotations.ClinicConverterFactory;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.PosixModuleBuiltins.ObjectToOpaquePathNode;
import com.oracle.graal.python.builtins.modules.PosixSubprocessModuleBuiltinsClinicProviders.NewForkExecNodeClinicProviderGen;
import com.oracle.graal.python.builtins.modules.PosixSubprocessModuleBuiltinsFactory.EnvConversionNodeGen;
import com.oracle.graal.python.builtins.modules.PosixSubprocessModuleBuiltinsFactory.ProcessArgsConversionNodeGen;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.bytes.BytesNodes.ToBytesNode;
import com.oracle.graal.python.builtins.objects.common.SequenceNodes.GetSequenceStorageNode;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes.GetItemNode;
import com.oracle.graal.python.builtins.objects.exception.OSErrorEnum;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.lib.PyObjectGetItem;
import com.oracle.graal.python.lib.PyObjectSizeNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.builtins.ListNodes.FastConstructListNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentCastNode.ArgumentCastNodeWithRaise;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.util.CannotCastException;
import com.oracle.graal.python.nodes.util.CastToJavaIntExactNode;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.PosixSupportLibrary;
import com.oracle.graal.python.runtime.PosixSupportLibrary.PosixException;
import com.oracle.graal.python.runtime.PythonOptions;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.runtime.sequence.storage.SequenceStorage;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(defineModule = "_posixsubprocess")
public class PosixSubprocessModuleBuiltins extends PythonBuiltins {
    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return PosixSubprocessModuleBuiltinsFactory.getFactories();
    }

    /**
     * Helper converter which iterates the argv argument and converts each element to the opaque
     * path representation used by {@link PosixSupportLibrary}.
     */
    abstract static class ProcessArgsConversionNode extends ArgumentCastNodeWithRaise {
        @Specialization
        static Object[] doNone(@SuppressWarnings("unused") PNone processArgs) {
            // CPython passes NULL to execve() in this case. man execve explicitly discourages this,
            // but says that on Linux it is equivalent to an empty array.
            return new Object[0];
        }

        @Specialization
        Object[] doSequence(VirtualFrame frame, Object processArgs,
                        @Cached FastConstructListNode fastConstructListNode,
                        @Cached GetSequenceStorageNode getSequenceStorageNode,
                        @Cached IsBuiltinClassProfile isBuiltinClassProfile,
                        @Cached ObjectToOpaquePathNode objectToOpaquePathNode,
                        @Cached("createNotNormalized()") GetItemNode getItemNode) {
            PSequence argsSequence;
            try {
                argsSequence = fastConstructListNode.execute(frame, processArgs);
            } catch (PException e) {
                e.expect(TypeError, isBuiltinClassProfile);
                throw raise(TypeError, ErrorMessages.S_MUST_BE_S, "argv", "a tuple");
            }

            SequenceStorage argsStorage = getSequenceStorageNode.execute(argsSequence);
            int len = argsStorage.length();
            Object[] argsArray = new Object[len];
            for (int i = 0; i < len; ++i) {
                SequenceStorage newStorage = getSequenceStorageNode.execute(argsSequence);
                if (newStorage != argsStorage || newStorage.length() != len) {
                    // TODO write a test for this
                    throw raise(RuntimeError, ErrorMessages.ARGS_CHANGED_DURING_ITERATION);
                }
                Object o = getItemNode.execute(argsStorage, i);
                argsArray[i] = objectToOpaquePathNode.execute(frame, o, false);
            }
            return argsArray;
        }

        @ClinicConverterFactory
        static ProcessArgsConversionNode create() {
            return ProcessArgsConversionNodeGen.create();
        }
    }

    abstract static class EnvConversionNode extends ArgumentCastNodeWithRaise {
        @Specialization
        static Object doNone(@SuppressWarnings("unused") PNone env) {
            return null;
        }

        @Specialization(guards = "!isPNone(env)")
        Object doSequence(VirtualFrame frame, Object env,
                        @Cached PyObjectSizeNode sizeNode,
                        @Cached ToBytesNode toBytesNode,
                        @Cached PyObjectGetItem getItem,
                        @CachedLibrary("getContext().getPosixSupport()") PosixSupportLibrary posixLib) {
            // TODO unlike CPython, this accepts a dict (if the keys are integers (0, 1, ..., len-1)
            int length = sizeNode.execute(frame, env);
            Object[] result = new Object[length];
            for (int i = 0; i < length; ++i) {
                Object o = getItem.execute(frame, env, i);
                byte[] bytes = toBytesNode.execute(frame, o);
                Object o1 = posixLib.createPathFromBytes(getContext().getPosixSupport(), bytes);
                if (o1 == null) {
                    throw raise(ValueError, ErrorMessages.EMBEDDED_NULL_BYTE);
                }
                result[i] = o1;
            }
            return result;
        }

        @ClinicConverterFactory
        static EnvConversionNode create() {
            return EnvConversionNodeGen.create();
        }
    }

    @Builtin(name = "fork_exec", minNumOfPositionalArgs = 17, parameterNames = {"args", "executable_list", "close_fds",
                    "fds_to_keep", "cwd", "env", "p2cread", "p2cwrite", "c2pread", "c2pwrite", "errread", "errwrite",
                    "errpipe_read", "errpipe_write", "restore_signals", "call_setsid", "gid_object", "groups_list",
                    "uid_object", "child_umask", "preexec_fn"})
    @ArgumentClinic(name = "args", conversionClass = ProcessArgsConversionNode.class)
    @ArgumentClinic(name = "close_fds", conversion = ClinicConversion.Boolean)
    @ArgumentClinic(name = "env", conversionClass = EnvConversionNode.class)
    @ArgumentClinic(name = "p2cread", conversion = ClinicConversion.Int)
    @ArgumentClinic(name = "p2cwrite", conversion = ClinicConversion.Int)
    @ArgumentClinic(name = "c2pread", conversion = ClinicConversion.Int)
    @ArgumentClinic(name = "c2pwrite", conversion = ClinicConversion.Int)
    @ArgumentClinic(name = "errread", conversion = ClinicConversion.Int)
    @ArgumentClinic(name = "errwrite", conversion = ClinicConversion.Int)
    @ArgumentClinic(name = "errpipe_read", conversion = ClinicConversion.Int)
    @ArgumentClinic(name = "errpipe_write", conversion = ClinicConversion.Int)
    @ArgumentClinic(name = "restore_signals", conversion = ClinicConversion.IntToBoolean)
    @ArgumentClinic(name = "call_setsid", conversion = ClinicConversion.IntToBoolean)
    @ArgumentClinic(name = "child_umask", conversion = ClinicConversion.Int)
    @GenerateNodeFactory
    abstract static class NewForkExecNode extends PythonClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return NewForkExecNodeClinicProviderGen.INSTANCE;
        }

        @TruffleBoundary
        private static byte[] fsEncode(String s) {
            // This is needed for the special case when someone uses sys.executable in call to
            // POpen, which converts it to bytes using os.fsencode
            // TODO Implement fsencode
            return s.getBytes();
        }

        private Object createPathFromBytes(byte[] bytes, PosixSupportLibrary posixLib) {
            Object o = posixLib.createPathFromBytes(getPosixSupport(), bytes);
            if (o == null) {
                // TODO reconsider the contract of PosixSupportLibrary#createPathFromBytes w.r.t.
                // embedded null checks (we need to review that anyway since PosixSupportLibrary
                // cannot do Python-specific fsencode)
                throw raise(ValueError, ErrorMessages.EMBEDDED_NULL_BYTE);
            }
            return o;
        }

        @SuppressWarnings("unused")
        @Specialization
        int forkExec(VirtualFrame frame, Object[] args, Object executableList, boolean closeFds,
                        Object fdsToKeepObj, Object cwdObj, Object env,
                        int stdinRead, int stdinWrite, int stdoutRead, int stdoutWrite,
                        int stderrRead, int stderrWrite, int errPipeRead, int errPipeWrite,
                        boolean restoreSignals, boolean callSetsid, Object gidObject, Object groupsList,
                        Object uidObject, int childUmask, Object preexecFn,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached("createNotNormalized()") GetItemNode tupleGetItem,
                        @Cached PyObjectGetItem getItem,
                        @Cached CastToJavaIntExactNode castToIntNode,
                        @Cached ObjectToOpaquePathNode objectToOpaquePathNode,
                        @Cached PyObjectSizeNode sizeNode,
                        @Cached GilNode gil,
                        @Cached ToBytesNode toBytesNode) {
            if (!(preexecFn instanceof PNone)) {
                throw raise(RuntimeError, ErrorMessages.S_NOT_SUPPORTED, "preexec_fn");
            }
            if (closeFds && errPipeWrite < 3) {
                throw raise(ValueError, ErrorMessages.S_MUST_BE_S, "errpipe_write", ">= 3");
            }
            if (!(fdsToKeepObj instanceof PTuple)) {
                throw raise(TypeError, ErrorMessages.ARG_D_MUST_BE_S_NOT_P, "fork_exec()", 4, "tuple", fdsToKeepObj);
            }
            Object[] processArgs = args;
            int[] fdsToKeep = convertFdSequence((PTuple) fdsToKeepObj, tupleGetItem, castToIntNode);
            Object cwd = PGuards.isPNone(cwdObj) ? null : objectToOpaquePathNode.execute(frame, cwdObj, false);

            byte[] sysExecutable = fsEncode(getContext().getOption(PythonOptions.Executable).toJavaStringUncached());

            // TODO unlike CPython, this accepts a dict (if the keys are integers (0, 1, ..., len-1)
            int length = sizeNode.execute(frame, executableList);
            Object[] executables = new Object[length];
            for (int i = 0; i < length; ++i) {
                byte[] bytes = toBytesNode.execute(frame, getItem.execute(frame, executableList, i));
                if (Arrays.equals(bytes, sysExecutable)) {
                    if (length != 1) {
                        throw raise(ValueError, ErrorMessages.UNSUPPORTED_USE_OF_SYS_EXECUTABLE);
                    }
                    TruffleString[] additionalArgs = PythonOptions.getExecutableList(getContext());
                    Object[] extendedArgs = new Object[additionalArgs.length + (processArgs.length == 0 ? 0 : processArgs.length - 1)];
                    for (int j = 0; j < additionalArgs.length; ++j) {
                        extendedArgs[j] = createPathFromBytes(fsEncode(additionalArgs[j].toJavaStringUncached()), posixLib);
                    }
                    if (processArgs.length > 1) {
                        PythonUtils.arraycopy(processArgs, 1, extendedArgs, additionalArgs.length, processArgs.length - 1);
                    }
                    processArgs = extendedArgs;
                    executables[i] = extendedArgs[0];
                } else {
                    executables[i] = createPathFromBytes(bytes, posixLib);
                }
            }

            gil.release(true);
            try {
                return posixLib.forkExec(getPosixSupport(), executables, processArgs, cwd, env == null ? null : (Object[]) env, stdinRead, stdinWrite, stdoutRead, stdoutWrite, stderrRead, stderrWrite,
                                errPipeRead, errPipeWrite, closeFds, restoreSignals, callSetsid, fdsToKeep);
            } catch (PosixException e) {
                gil.acquire();
                throw raiseOSErrorFromPosixException(frame, e);
            } catch (SecurityException e) {
                gil.acquire();
                throw raiseOSError(frame, OSErrorEnum.EPERM);
            } finally {
                gil.acquire();
            }
        }

        /**
         * Checks that the tuple contains only valid fds (positive integers fitting into an int) in
         * ascending order.
         */
        private int[] convertFdSequence(PTuple fdSequence, GetItemNode getItemNode, CastToJavaIntExactNode castToIntNode) {
            SequenceStorage storage = fdSequence.getSequenceStorage();
            int len = storage.length();
            int[] fds = new int[len];
            int prevFd = -1;
            for (int i = 0; i < len; ++i) {
                try {
                    int fd = castToIntNode.execute(getItemNode.execute(storage, i));
                    if (fd > prevFd) {
                        prevFd = fds[i] = fd;
                        continue;
                    }
                } catch (PException | CannotCastException e) {
                    // 'handled' by raise() below
                }
                throw raise(ValueError, ErrorMessages.BAD_VALUES_IN_FDS_TO_KEEP);
            }
            return fds;
        }

    }
}

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

import static com.oracle.graal.python.runtime.PosixConstants.FD_SETSIZE;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;
import static com.oracle.graal.python.util.TimeUtils.SEC_TO_NS;

import java.util.List;

import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.list.PList;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.lib.PyObjectAsFileDescriptor;
import com.oracle.graal.python.lib.PyObjectGetItem;
import com.oracle.graal.python.lib.PyObjectSizeNode;
import com.oracle.graal.python.lib.PyTimeFromObjectNode;
import com.oracle.graal.python.lib.PyTimeFromObjectNode.RoundType;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.builtins.ListNodes.FastConstructListNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.PosixSupportLibrary;
import com.oracle.graal.python.runtime.PosixSupportLibrary.ChannelNotSelectableException;
import com.oracle.graal.python.runtime.PosixSupportLibrary.PosixException;
import com.oracle.graal.python.runtime.PosixSupportLibrary.SelectResult;
import com.oracle.graal.python.runtime.PosixSupportLibrary.Timeval;
import com.oracle.graal.python.runtime.exception.PythonErrorType;
import com.oracle.graal.python.runtime.sequence.PSequence;
import com.oracle.graal.python.util.ArrayBuilder;
import com.oracle.graal.python.util.IntArrayBuilder;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.graal.python.util.TimeUtils;
import com.oracle.truffle.api.CompilerDirectives.ValueType;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.BranchProfile;

@CoreFunctions(defineModule = "select")
public class SelectModuleBuiltins extends PythonBuiltins {

    /*
     * ATTENTION: if we ever add "poll" support, update the code in
     * MultiprocessingModuleBuilins#SelectNode to use it if available
     */

    public SelectModuleBuiltins() {
        addBuiltinConstant("error", PythonErrorType.OSError);
    }

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return SelectModuleBuiltinsFactory.getFactories();
    }

    @Builtin(name = "select", minNumOfPositionalArgs = 3, parameterNames = {"rlist", "wlist", "xlist", "timeout"})
    @GenerateNodeFactory
    abstract static class SelectNode extends PythonBuiltinNode {

        @Specialization
        PTuple doWithoutTimeout(VirtualFrame frame, Object rlist, Object wlist, Object xlist, @SuppressWarnings("unused") PNone timeout,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached PyObjectSizeNode sizeNode,
                        @Cached PyObjectGetItem callGetItemNode,
                        @Cached FastConstructListNode constructListNode,
                        @Cached PyTimeFromObjectNode pyTimeFromObjectNode,
                        @Cached PyObjectAsFileDescriptor asFileDescriptor,
                        @Cached BranchProfile notSelectableBranch,
                        @Cached GilNode gil) {
            return doGeneric(frame, rlist, wlist, xlist, PNone.NONE, posixLib, sizeNode,
                            callGetItemNode, constructListNode, pyTimeFromObjectNode, asFileDescriptor, notSelectableBranch, gil);
        }

        @Specialization(replaces = "doWithoutTimeout")
        PTuple doGeneric(VirtualFrame frame, Object rlist, Object wlist, Object xlist, Object timeout,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached PyObjectSizeNode sizeNode,
                        @Cached PyObjectGetItem callGetItemNode,
                        @Cached FastConstructListNode constructListNode,
                        @Cached PyTimeFromObjectNode pyTimeFromObjectNode,
                        @Cached PyObjectAsFileDescriptor asFileDescriptor,
                        @Cached BranchProfile notSelectableBranch,
                        @Cached GilNode gil) {
            ObjAndFDList readFDs = seq2set(frame, rlist, sizeNode, asFileDescriptor, callGetItemNode, constructListNode);
            ObjAndFDList writeFDs = seq2set(frame, wlist, sizeNode, asFileDescriptor, callGetItemNode, constructListNode);
            ObjAndFDList xFDs = seq2set(frame, xlist, sizeNode, asFileDescriptor, callGetItemNode, constructListNode);

            Timeval timeoutval = null;
            if (!PGuards.isPNone(timeout)) {
                timeoutval = TimeUtils.pyTimeAsTimeval(pyTimeFromObjectNode.execute(frame, timeout, RoundType.TIMEOUT, SEC_TO_NS));
                if (timeoutval.getSeconds() < 0) {
                    throw raise(PythonBuiltinClassType.ValueError, ErrorMessages.MUST_BE_NON_NEGATIVE, "timeout");
                }
            }

            SelectResult result;
            try {
                gil.release(true);
                try {
                    result = posixLib.select(getPosixSupport(), readFDs.fds, writeFDs.fds, xFDs.fds, timeoutval);
                } finally {
                    gil.acquire();
                }
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e);
            } catch (ChannelNotSelectableException e) {
                // GraalPython hack: if one of the channels is not selectable (can happen only in
                // the emulated mode), we just return everything.
                notSelectableBranch.enter();
                return factory().createTuple(new Object[]{rlist, wlist, xlist});
            }
            return factory().createTuple(new PList[]{
                            toList(result.getReadFds(), readFDs),
                            toList(result.getWriteFds(), writeFDs),
                            toList(result.getErrorFds(), xFDs)});
        }

        /**
         * Also maps the returned FDs back to their original Python level objects.
         */
        private PList toList(boolean[] result, ObjAndFDList fds) {
            Object[] resultObjs = new Object[result.length];
            int resultObjsIdx = 0;
            for (int i = 0; i < fds.fds.length; i++) {
                if (result[i]) {
                    resultObjs[resultObjsIdx++] = fds.objects[i];
                }
            }
            return factory().createList(PythonUtils.arrayCopyOf(resultObjs, resultObjsIdx));
        }

        private ObjAndFDList seq2set(VirtualFrame frame, Object sequence, PyObjectSizeNode sizeNode, PyObjectAsFileDescriptor asFileDescriptor, PyObjectGetItem callGetItemNode,
                        FastConstructListNode constructListNode) {
            // We cannot assume any size of those two arrays, because the sequence may change as a
            // side effect of the invocation of fileno. We also need to call PyObjectSizeNode
            // repeatedly in the loop condition
            ArrayBuilder<Object> objects = new ArrayBuilder<>();
            IntArrayBuilder fds = new IntArrayBuilder();
            PSequence pSequence = constructListNode.execute(frame, sequence);
            for (int i = 0; i < sizeNode.execute(frame, sequence); i++) {
                Object pythonObject = callGetItemNode.execute(frame, pSequence, i);
                objects.add(pythonObject);
                int fd = asFileDescriptor.execute(frame, pythonObject);
                if (fd >= FD_SETSIZE.value) {
                    throw raise(ValueError, ErrorMessages.FILE_DESCRIPTOR_OUT_OF_RANGE_IN_SELECT);
                }
                fds.add(fd);
            }
            return new ObjAndFDList(objects.toArray(new Object[0]), fds.toArray());
        }

        @ValueType
        private static final class ObjAndFDList {
            private final Object[] objects;
            private final int[] fds;

            private ObjAndFDList(Object[] objects, int[] fds) {
                this.objects = objects;
                this.fds = fds;
            }
        }
    }
}

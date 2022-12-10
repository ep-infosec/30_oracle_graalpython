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
package com.oracle.graal.python.builtins.objects.socket;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.MemoryError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.OSError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.TypeError;
import static com.oracle.graal.python.builtins.PythonBuiltinClassType.ValueError;
import static com.oracle.graal.python.builtins.objects.exception.OSErrorEnum.EBADF;
import static com.oracle.graal.python.builtins.objects.exception.OSErrorEnum.EINPROGRESS;
import static com.oracle.graal.python.builtins.objects.exception.OSErrorEnum.EINTR;
import static com.oracle.graal.python.builtins.objects.exception.OSErrorEnum.EISCONN;
import static com.oracle.graal.python.builtins.objects.exception.OSErrorEnum.ENOTSOCK;
import static com.oracle.graal.python.builtins.objects.socket.PSocket.INVALID_FD;
import static com.oracle.graal.python.nodes.BuiltinNames.T__SOCKET;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___INIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___REPR__;
import static com.oracle.graal.python.runtime.PosixConstants.SOL_SOCKET;
import static com.oracle.graal.python.runtime.PosixConstants.SO_ERROR;
import static com.oracle.graal.python.runtime.PosixConstants.SO_PROTOCOL;
import static com.oracle.graal.python.runtime.PosixConstants.SO_TYPE;

import java.util.List;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.SocketModuleBuiltins;
import com.oracle.graal.python.builtins.modules.SysModuleBuiltins;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAccessLibrary;
import com.oracle.graal.python.builtins.objects.buffer.PythonBufferAcquireLibrary;
import com.oracle.graal.python.builtins.objects.bytes.PBytes;
import com.oracle.graal.python.builtins.objects.exception.OSErrorEnum;
import com.oracle.graal.python.builtins.objects.socket.SocketUtils.TimeoutHelper;
import com.oracle.graal.python.builtins.objects.str.StringUtils.SimpleTruffleStringFormatNode;
import com.oracle.graal.python.lib.PyLongAsIntNode;
import com.oracle.graal.python.nodes.ErrorMessages;
import com.oracle.graal.python.nodes.PConstructAndRaiseNode;
import com.oracle.graal.python.nodes.PNodeWithRaise;
import com.oracle.graal.python.nodes.attributes.ReadAttributeFromObjectNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonQuaternaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.runtime.GilNode;
import com.oracle.graal.python.runtime.PosixConstants;
import com.oracle.graal.python.runtime.PosixSupportLibrary;
import com.oracle.graal.python.runtime.PosixSupportLibrary.PosixException;
import com.oracle.graal.python.runtime.PosixSupportLibrary.RecvfromResult;
import com.oracle.graal.python.runtime.PosixSupportLibrary.UniversalSockAddr;
import com.oracle.graal.python.runtime.PosixSupportLibrary.UniversalSockAddrLibrary;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.graal.python.runtime.exception.PException;
import com.oracle.graal.python.util.PythonUtils;
import com.oracle.graal.python.util.TimeUtils;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.Fallback;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.api.strings.TruffleString;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PSocket)
public class SocketBuiltins extends PythonBuiltins {

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return SocketBuiltinsFactory.getFactories();
    }

    private static void checkSelectable(PNodeWithRaise node, PSocket socket) {
        if (!isSelectable(socket)) {
            throw node.raise(OSError, ErrorMessages.UNABLE_TO_SELECT_ON_SOCKET);
        }
    }

    private static boolean isSelectable(PSocket socket) {
        return socket.getTimeoutNs() <= 0 || socket.getFd() < PosixConstants.FD_SETSIZE.value;
    }

    @Builtin(name = J___INIT__, minNumOfPositionalArgs = 1, parameterNames = {"$self", "family", "type", "proto", "fileno"})
    @ArgumentClinic(name = "family", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "-1")
    @ArgumentClinic(name = "type", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "-1")
    @ArgumentClinic(name = "proto", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "-1")
    @GenerateNodeFactory
    public abstract static class InitNode extends PythonClinicBuiltinNode {
        @Specialization
        Object init(VirtualFrame frame, PSocket self, int familyIn, int typeIn, int protoIn, @SuppressWarnings("unused") PNone fileno,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Shared("auditNode") @Cached SysModuleBuiltins.AuditNode auditNode,
                        @Shared("readNode") @Cached ReadAttributeFromObjectNode readNode,
                        @Cached GilNode gil) {
            // sic! CPython really has __new__ there, even though it's in __init__
            auditNode.audit("socket.__new__", self, familyIn, typeIn, protoIn);
            int family = familyIn;
            if (family == -1) {
                family = PosixConstants.AF_INET.value;
            }
            int type = typeIn;
            if (type == -1) {
                type = PosixConstants.SOCK_STREAM.value;
            }
            int proto = protoIn;
            if (proto == -1) {
                proto = 0;
            }
            try {
                // TODO SOCK_CLOEXEC?
                int fd;
                gil.release(true);
                try {
                    fd = posixLib.socket(getPosixSupport(), family, type, proto);
                } finally {
                    gil.acquire();
                }
                try {
                    posixLib.setInheritable(getPosixSupport(), fd, false);
                    sockInit(posixLib, readNode, self, fd, family, type, proto);
                } catch (Exception e) {
                    // If we failed before giving the fd to python-land, close it
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    try {
                        posixLib.close(getPosixSupport(), fd);
                    } catch (PosixException posixException) {
                        // ignore
                    }
                    throw e;
                }
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e);
            }
            return PNone.NONE;
        }

        @Specialization(guards = "!isPNone(fileno)")
        Object init(VirtualFrame frame, PSocket self, int familyIn, int typeIn, int protoIn, Object fileno,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @CachedLibrary(limit = "1") UniversalSockAddrLibrary addrLib,
                        @Shared("auditNode") @Cached SysModuleBuiltins.AuditNode auditNode,
                        @Shared("readNode") @Cached ReadAttributeFromObjectNode readNode,
                        @Cached PyLongAsIntNode asIntNode) {
            // sic! CPython really has __new__ there, even though it's in __init__
            auditNode.audit("socket.__new__", self, familyIn, typeIn, protoIn);

            int fd = asIntNode.execute(frame, fileno);
            if (fd < 0) {
                throw raise(ValueError, ErrorMessages.NEG_FILE_DESC);
            }
            int family = familyIn;
            try {
                UniversalSockAddr addr = posixLib.getsockname(getPosixSupport(), fd);
                if (family == -1) {
                    family = addrLib.getFamily(addr);
                }
            } catch (PosixException e) {
                if (family == -1 || e.getErrorCode() == EBADF.getNumber() || e.getErrorCode() == ENOTSOCK.getNumber()) {
                    throw raiseOSErrorFromPosixException(frame, e);
                }
            }
            try {
                int type = typeIn;
                if (type == -1) {
                    type = getIntSockopt(posixLib, fd, SOL_SOCKET.value, SO_TYPE.value);
                }
                int proto = protoIn;
                if (SO_PROTOCOL.defined) {
                    if (proto == -1) {
                        proto = getIntSockopt(posixLib, fd, SOL_SOCKET.value, SO_PROTOCOL.getValueIfDefined());
                    }
                } else {
                    proto = 0;
                }
                sockInit(posixLib, readNode, self, fd, family, type, proto);
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e);
            }
            return PNone.NONE;
        }

        private void sockInit(PosixSupportLibrary posixLib, ReadAttributeFromObjectNode readNode, PSocket self, int fd, int family, int type, int proto) throws PosixException {
            self.setFd(fd);
            self.setFamily(family);
            // TODO remove SOCK_CLOEXEC and SOCK_NONBLOCK
            self.setType(type);
            self.setProto(proto);
            long defaultTimeout = (long) readNode.execute(getContext().lookupBuiltinModule(T__SOCKET), SocketModuleBuiltins.DEFAULT_TIMEOUT_KEY);
            self.setTimeoutNs(defaultTimeout);
            if (defaultTimeout >= 0) {
                posixLib.setBlocking(getPosixSupport(), fd, false);
            }
        }

        private int getIntSockopt(PosixSupportLibrary posixLib, int fd, int level, int option) throws PosixException {
            byte[] tmp = new byte[4];
            int len = posixLib.getsockopt(getPosixSupport(), fd, level, option, tmp, tmp.length);
            assert len == tmp.length;
            return PythonUtils.arrayAccessor.getInt(tmp, 0);
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return SocketBuiltinsClinicProviders.InitNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = J___REPR__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReprNode extends PythonUnaryBuiltinNode {
        @Specialization
        TruffleString repr(PSocket self,
                        @Cached SimpleTruffleStringFormatNode simpleTruffleStringFormatNode) {
            return simpleTruffleStringFormatNode.format("<socket object, fd=%d, family=%d, type=%d, proto=%d>", self.getFd(), self.getFamily(), self.getType(), self.getProto());
        }
    }

    // accept()
    @Builtin(name = "_accept", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class AcceptNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object accept(VirtualFrame frame, PSocket self,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached SocketNodes.MakeSockAddrNode makeSockAddrNode,
                        @Cached GilNode gil) {
            checkSelectable(this, self);

            try {
                PosixSupportLibrary.AcceptResult acceptResult = SocketUtils.callSocketFunctionWithRetry(frame, getConstructAndRaiseNode(), posixLib, getPosixSupport(), gil, self,
                                () -> posixLib.accept(getPosixSupport(), self.getFd()),
                                false, false);
                try {
                    Object pythonAddr = makeSockAddrNode.execute(frame, acceptResult.sockAddr);
                    posixLib.setInheritable(getPosixSupport(), acceptResult.socketFd, false);
                    return factory().createTuple(new Object[]{acceptResult.socketFd, pythonAddr});
                } catch (Exception e) {
                    // If we failed before giving the fd to python-land, close it
                    CompilerDirectives.transferToInterpreterAndInvalidate();
                    try {
                        posixLib.close(getPosixSupport(), acceptResult.socketFd);
                    } catch (PosixException posixException) {
                        // ignore
                    }
                    throw e;
                }
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e);
            }
        }
    }

    // bind(address)
    @Builtin(name = "bind", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class BindNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object bind(VirtualFrame frame, PSocket self, Object address,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLibrary,
                        @Cached SocketNodes.GetSockAddrArgNode getSockAddrArgNode,
                        @Cached SysModuleBuiltins.AuditNode auditNode,
                        @Cached GilNode gil) {
            UniversalSockAddr addr = getSockAddrArgNode.execute(frame, self, address, "bind");
            auditNode.audit("socket.bind", self, address);

            try {
                gil.release(true);
                try {
                    posixLibrary.bind(getPosixSupport(), self.getFd(), addr);
                } finally {
                    gil.acquire();
                }
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e);
            }
            return PNone.NONE;
        }
    }

    // close()
    @Builtin(name = "close", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class CloseNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object close(VirtualFrame frame, PSocket socket,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached GilNode gil) {
            int fd = socket.getFd();
            if (fd != INVALID_FD) {
                try {
                    socket.setFd(INVALID_FD);
                    gil.release(true);
                    try {
                        posixLib.close(getPosixSupport(), fd);
                    } finally {
                        gil.acquire();
                    }
                } catch (PosixException e) {
                    // CPython ignores ECONNRESET on close
                    if (e.getErrorCode() != OSErrorEnum.ECONNRESET.getNumber()) {
                        throw raiseOSErrorFromPosixException(frame, e);
                    }
                }
            }
            return PNone.NONE;
        }
    }

    // connect(address)
    @Builtin(name = "connect", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class ConnectNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object connect(VirtualFrame frame, PSocket self, Object address,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached SocketNodes.GetSockAddrArgNode getSockAddrArgNode,
                        @Cached GilNode gil,
                        @Cached SysModuleBuiltins.AuditNode auditNode) {
            UniversalSockAddr connectAddr = getSockAddrArgNode.execute(frame, self, address, "connect");

            auditNode.audit("socket.connect", self, address);

            try {
                doConnect(frame, getConstructAndRaiseNode(), posixLib, getPosixSupport(), gil, self, connectAddr);
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e);
            }
            return PNone.NONE;
        }

        static void doConnect(Frame frame, PConstructAndRaiseNode constructAndRaiseNode, PosixSupportLibrary posixLib, Object posixSupport, GilNode gil, PSocket self, UniversalSockAddr connectAddr)
                        throws PosixException {
            try {
                gil.release(true);
                try {
                    posixLib.connect(posixSupport, self.getFd(), connectAddr);
                } finally {
                    gil.acquire();
                }
            } catch (PosixException e) {
                boolean waitConnect;
                if (e.getErrorCode() == EINTR.getNumber()) {
                    PythonContext.triggerAsyncActions(constructAndRaiseNode);
                    waitConnect = self.getTimeoutNs() != 0 && isSelectable(self);
                } else {
                    waitConnect = self.getTimeoutNs() > 0 && e.getErrorCode() == EINPROGRESS.getNumber() && isSelectable(self);
                }
                if (waitConnect) {
                    SocketUtils.callSocketFunctionWithRetry(frame, constructAndRaiseNode, posixLib, posixSupport, gil, self,
                                    () -> {
                                        byte[] tmp = new byte[4];
                                        posixLib.getsockopt(posixSupport, self.getFd(), SOL_SOCKET.value, SO_ERROR.value, tmp, tmp.length);
                                        int err = PythonUtils.arrayAccessor.getInt(tmp, 0);
                                        if (err != 0 && err != EISCONN.getNumber()) {
                                            throw new PosixException(err, posixLib.strerror(posixSupport, err));
                                        }
                                        return null;
                                    },
                                    true, true);
                } else {
                    throw e;
                }
            }
        }
    }

    // connect_ex(address)
    @Builtin(name = "connect_ex", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class ConnectExNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object connectEx(VirtualFrame frame, PSocket self, Object address,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached SocketNodes.GetSockAddrArgNode getSockAddrArgNode,
                        @Cached GilNode gil,
                        @Cached SysModuleBuiltins.AuditNode auditNode) {
            UniversalSockAddr connectAddr = getSockAddrArgNode.execute(frame, self, address, "connect_ex");

            auditNode.audit("socket.connect", self, address); // sic! connect

            try {
                ConnectNode.doConnect(frame, getConstructAndRaiseNode(), posixLib, getPosixSupport(), gil, self, connectAddr);
            } catch (PosixException e) {
                return e.getErrorCode();
            }
            return 0;
        }
    }

    // getpeername()
    @Builtin(name = "getpeername", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class GetPeerNameNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object get(VirtualFrame frame, PSocket socket,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached SocketNodes.MakeSockAddrNode makeSockAddrNode,
                        @Cached GilNode gil) {
            try {
                UniversalSockAddr addr;
                gil.release(true);
                try {
                    addr = posixLib.getpeername(getPosixSupport(), socket.getFd());
                } finally {
                    gil.acquire();
                }
                return makeSockAddrNode.execute(frame, addr);
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e);
            }
        }
    }

    // getsockname()
    @Builtin(name = "getsockname", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class GetSockNameNode extends PythonUnaryBuiltinNode {
        @Specialization
        Object get(VirtualFrame frame, PSocket socket,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached SocketNodes.MakeSockAddrNode makeSockAddrNode,
                        @Cached GilNode gil) {
            try {
                UniversalSockAddr addr;
                gil.release(true);
                try {
                    addr = posixLib.getsockname(getPosixSupport(), socket.getFd());
                } finally {
                    gil.acquire();
                }
                return makeSockAddrNode.execute(frame, addr);
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e);
            }
        }
    }

    // getblocking()
    @Builtin(name = "getblocking", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    public abstract static class GetBlockingNode extends PythonUnaryBuiltinNode {
        @Specialization
        public static boolean get(PSocket socket) {
            return socket.getTimeoutNs() != 0;
        }
    }

    // gettimeout
    @Builtin(name = "gettimeout", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class GetTimeoutNode extends PythonUnaryBuiltinNode {
        @Specialization
        static Object get(PSocket socket) {
            if (socket.getTimeoutNs() < 0) {
                return PNone.NONE;
            } else {
                return TimeUtils.pyTimeAsSecondsDouble(socket.getTimeoutNs());
            }
        }
    }

    // listen
    @Builtin(name = "listen", minNumOfPositionalArgs = 1, numOfPositionalOnlyArgs = 2, parameterNames = {"$self", "backlog"})
    @ArgumentClinic(name = "backlog", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "128")
    @GenerateNodeFactory
    abstract static class ListenNode extends PythonBinaryClinicBuiltinNode {
        @Specialization
        Object listen(VirtualFrame frame, PSocket self, int backlogIn,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached GilNode gil) {
            int backlog = backlogIn;
            if (backlog < 0) {
                backlog = 0;
            }
            try {
                gil.release(true);
                try {
                    posixLib.listen(getPosixSupport(), self.getFd(), backlog);
                } finally {
                    gil.acquire();
                }
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e);
            }
            return PNone.NONE;
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return SocketBuiltinsClinicProviders.ListenNodeClinicProviderGen.INSTANCE;
        }
    }

    // recv(bufsize[, flags])
    @Builtin(name = "recv", minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 3, parameterNames = {"$self", "nbytes", "flags"})
    @ArgumentClinic(name = "nbytes", conversion = ArgumentClinic.ClinicConversion.Index)
    @ArgumentClinic(name = "flags", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "0")
    @GenerateNodeFactory
    abstract static class RecvNode extends PythonTernaryClinicBuiltinNode {
        @Specialization
        Object recv(VirtualFrame frame, PSocket socket, int recvlen, int flags,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached GilNode gil) {
            if (recvlen < 0) {
                throw raise(ValueError, ErrorMessages.NEG_BUFF_SIZE_IN_RECV);
            }
            checkSelectable(this, socket);
            if (recvlen == 0) {
                return factory().createBytes(PythonUtils.EMPTY_BYTE_ARRAY);
            }

            byte[] bytes;
            try {
                bytes = new byte[recvlen];
            } catch (OutOfMemoryError error) {
                throw raise(MemoryError);
            }

            try {
                int outlen = SocketUtils.callSocketFunctionWithRetry(frame, getConstructAndRaiseNode(), posixLib, getPosixSupport(), gil, socket,
                                () -> posixLib.recv(getPosixSupport(), socket.getFd(), bytes, 0, bytes.length, flags),
                                false, false);
                if (outlen == 0) {
                    return factory().createBytes(PythonUtils.EMPTY_BYTE_ARRAY);
                }
                // TODO maybe resize if much smaller?
                return factory().createBytes(bytes, outlen);
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e);
            }
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return SocketBuiltinsClinicProviders.RecvNodeClinicProviderGen.INSTANCE;
        }
    }

    // recvfrom(bufsize[, flags])
    @Builtin(name = "recvfrom", minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 3, parameterNames = {"$self", "nbytes", "flags"})
    @ArgumentClinic(name = "nbytes", conversion = ArgumentClinic.ClinicConversion.Index)
    @ArgumentClinic(name = "flags", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "0")
    @GenerateNodeFactory
    abstract static class RecvFromNode extends PythonTernaryClinicBuiltinNode {
        @Specialization
        Object recvFrom(VirtualFrame frame, PSocket socket, int recvlen, int flags,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached GilNode gil,
                        @Cached SocketNodes.MakeSockAddrNode makeSockAddrNode) {
            if (recvlen < 0) {
                throw raise(ValueError, ErrorMessages.NEG_BUFF_SIZE_IN_RECVFROM);
            }
            checkSelectable(this, socket);

            byte[] bytes;
            try {
                bytes = new byte[recvlen];
            } catch (OutOfMemoryError error) {
                throw raise(MemoryError);
            }

            try {
                RecvfromResult result = SocketUtils.callSocketFunctionWithRetry(frame, getConstructAndRaiseNode(), posixLib, getPosixSupport(), gil, socket,
                                () -> posixLib.recvfrom(getPosixSupport(), socket.getFd(), bytes, 0, bytes.length, flags),
                                false, false);
                PBytes resultBytes;
                if (result.readBytes == 0) {
                    resultBytes = factory().createBytes(PythonUtils.EMPTY_BYTE_ARRAY);
                } else {
                    // TODO maybe resize if much smaller?
                    resultBytes = factory().createBytes(bytes, result.readBytes);
                }
                return factory().createTuple(new Object[]{resultBytes, makeSockAddrNode.execute(frame, result.sockAddr)});
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e);
            }
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return SocketBuiltinsClinicProviders.RecvFromNodeClinicProviderGen.INSTANCE;
        }
    }

    // recv_into(bufsize[, flags])
    @Builtin(name = "recv_into", minNumOfPositionalArgs = 2, parameterNames = {"$self", "buffer", "nbytes", "flags"})
    @ArgumentClinic(name = "nbytes", conversion = ArgumentClinic.ClinicConversion.Index, defaultValue = "0")
    @ArgumentClinic(name = "flags", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "0")
    @GenerateNodeFactory
    abstract static class RecvIntoNode extends PythonQuaternaryClinicBuiltinNode {
        @Specialization(limit = "3")
        Object recvInto(VirtualFrame frame, PSocket socket, Object bufferObj, int recvlenIn, int flags,
                        @CachedLibrary("bufferObj") PythonBufferAcquireLibrary bufferAcquireLib,
                        @CachedLibrary(limit = "1") PythonBufferAccessLibrary bufferLib,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached GilNode gil) {
            Object buffer = bufferAcquireLib.acquireWritable(bufferObj, frame, this);
            try {
                if (recvlenIn < 0) {
                    throw raise(ValueError, ErrorMessages.NEG_BUFF_SIZE_IN_RECV_INTO);
                }
                int buflen = bufferLib.getBufferLength(buffer);
                int recvlen = recvlenIn;
                if (recvlen == 0) {
                    recvlen = buflen;
                }
                if (buflen < recvlen) {
                    throw raise(ValueError, ErrorMessages.BUFF_TOO_SMALL);
                }

                checkSelectable(this, socket);

                boolean directWrite = bufferLib.hasInternalByteArray(buffer);
                byte[] bytes;
                if (directWrite) {
                    bytes = bufferLib.getInternalByteArray(buffer);
                } else {
                    try {
                        bytes = new byte[recvlen];
                    } catch (OutOfMemoryError error) {
                        throw raise(MemoryError);
                    }
                }

                final int len = recvlen;
                try {
                    int outlen = SocketUtils.callSocketFunctionWithRetry(frame, getConstructAndRaiseNode(), posixLib, getPosixSupport(), gil, socket,
                                    () -> posixLib.recv(getPosixSupport(), socket.getFd(), bytes, 0, len, flags),
                                    false, false);
                    if (!directWrite) {
                        bufferLib.readIntoByteArray(buffer, 0, bytes, 0, outlen);
                    }
                    return outlen;
                } catch (PosixException e) {
                    throw raiseOSErrorFromPosixException(frame, e);
                }
            } finally {
                bufferLib.release(bufferObj, frame, this);
            }
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return SocketBuiltinsClinicProviders.RecvIntoNodeClinicProviderGen.INSTANCE;
        }
    }

    // recvfrom_into(buffer[, nbytes [,flags]])
    @Builtin(name = "recvfrom_into", minNumOfPositionalArgs = 2, parameterNames = {"$self", "buffer", "nbytes", "flags"})
    @ArgumentClinic(name = "nbytes", conversion = ArgumentClinic.ClinicConversion.Index, defaultValue = "0")
    @ArgumentClinic(name = "flags", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "0")
    @GenerateNodeFactory
    abstract static class RecvFromIntoNode extends PythonQuaternaryClinicBuiltinNode {
        @Specialization(limit = "3")
        Object recvFromInto(VirtualFrame frame, PSocket socket, Object bufferObj, int recvlenIn, int flags,
                        @CachedLibrary("bufferObj") PythonBufferAcquireLibrary bufferAcquireLib,
                        @CachedLibrary(limit = "1") PythonBufferAccessLibrary bufferLib,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached GilNode gil,
                        @Cached SocketNodes.MakeSockAddrNode makeSockAddrNode) {
            Object buffer = bufferAcquireLib.acquireWritable(bufferObj, frame, this);
            try {
                if (recvlenIn < 0) {
                    throw raise(ValueError, ErrorMessages.NEG_BUFF_SIZE_IN_RECVFROM_INTO);
                }
                int buflen = bufferLib.getBufferLength(buffer);
                int recvlen = recvlenIn;
                if (recvlen == 0) {
                    recvlen = buflen;
                }
                if (buflen < recvlen) {
                    throw raise(ValueError, ErrorMessages.NBYTES_GREATER_THAT_BUFF);
                }

                checkSelectable(this, socket);

                boolean directWrite = bufferLib.hasInternalByteArray(buffer);
                byte[] bytes;
                if (directWrite) {
                    bytes = bufferLib.getInternalByteArray(buffer);
                } else {
                    try {
                        bytes = new byte[recvlen];
                    } catch (OutOfMemoryError error) {
                        throw raise(MemoryError);
                    }
                }

                try {
                    RecvfromResult result = SocketUtils.callSocketFunctionWithRetry(frame, getConstructAndRaiseNode(), posixLib, getPosixSupport(), gil, socket,
                                    () -> posixLib.recvfrom(getPosixSupport(), socket.getFd(), bytes, 0, bytes.length, flags),
                                    false, false);
                    if (!directWrite) {
                        bufferLib.readIntoByteArray(buffer, 0, bytes, 0, result.readBytes);
                    }
                    return factory().createTuple(new Object[]{result.readBytes, makeSockAddrNode.execute(frame, result.sockAddr)});
                } catch (PosixException e) {
                    throw raiseOSErrorFromPosixException(frame, e);
                }
            } finally {
                bufferLib.release(buffer, frame, this);
            }
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return SocketBuiltinsClinicProviders.RecvFromIntoNodeClinicProviderGen.INSTANCE;
        }
    }

    // send(bytes[, flags])
    @Builtin(name = "send", minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 2, parameterNames = {"$self", "buffer", "flags"})
    @ArgumentClinic(name = "flags", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "0")
    @GenerateNodeFactory
    abstract static class SendNode extends PythonTernaryClinicBuiltinNode {
        @Specialization(limit = "3")
        int send(VirtualFrame frame, PSocket socket, Object bufferObj, int flags,
                        @CachedLibrary("bufferObj") PythonBufferAcquireLibrary bufferAcquireLib,
                        @CachedLibrary(limit = "1") PythonBufferAccessLibrary bufferLib,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached GilNode gil) {
            Object buffer = bufferAcquireLib.acquireReadonly(bufferObj, frame, this);
            try {
                checkSelectable(this, socket);

                int len = bufferLib.getBufferLength(buffer);
                byte[] bytes = bufferLib.getInternalOrCopiedByteArray(buffer);

                try {
                    return SocketUtils.callSocketFunctionWithRetry(frame, getConstructAndRaiseNode(), posixLib, getPosixSupport(), gil, socket,
                                    () -> posixLib.send(getPosixSupport(), socket.getFd(), bytes, 0, len, flags),
                                    true, false);
                } catch (PosixException e) {
                    throw raiseOSErrorFromPosixException(frame, e);
                }
            } finally {
                bufferLib.release(buffer, frame, this);
            }
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return SocketBuiltinsClinicProviders.SendNodeClinicProviderGen.INSTANCE;
        }
    }

    // sendall(bytes[, flags])
    @Builtin(name = "sendall", minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 3, parameterNames = {"$self", "buffer", "flags"})
    @ArgumentClinic(name = "flags", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "0")
    @GenerateNodeFactory
    abstract static class SendAllNode extends PythonTernaryClinicBuiltinNode {
        @Specialization(limit = "3")
        Object sendAll(VirtualFrame frame, PSocket socket, Object bufferObj, int flags,
                        @CachedLibrary("bufferObj") PythonBufferAcquireLibrary bufferAcquireLib,
                        @CachedLibrary(limit = "1") PythonBufferAccessLibrary bufferLib,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached GilNode gil) {
            Object buffer = bufferAcquireLib.acquireReadonly(bufferObj, frame, this);
            try {
                checkSelectable(this, socket);

                int offset = 0;
                int len = bufferLib.getBufferLength(buffer);
                byte[] bytes = bufferLib.getInternalOrCopiedByteArray(buffer);

                long timeout = socket.getTimeoutNs();
                TimeoutHelper timeoutHelper = null;
                if (timeout > 0) {
                    timeoutHelper = new TimeoutHelper(timeout);
                }

                while (true) {
                    try {
                        final int offset1 = offset;
                        final int len1 = len;
                        int outlen = SocketUtils.callSocketFunctionWithRetry(frame, getConstructAndRaiseNode(), posixLib, getPosixSupport(), gil, socket,
                                        () -> posixLib.send(getPosixSupport(), socket.getFd(), bytes, offset1, len1, flags),
                                        true, false, timeoutHelper);
                        offset += outlen;
                        len -= outlen;
                        if (len <= 0) {
                            return PNone.NONE;
                        }
                        // This can loop for a potentially long time
                        PythonContext.triggerAsyncActions(this);
                    } catch (PosixException e) {
                        throw raiseOSErrorFromPosixException(frame, e);
                    }
                }
            } finally {
                bufferLib.release(buffer, frame, this);
            }
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return SocketBuiltinsClinicProviders.SendAllNodeClinicProviderGen.INSTANCE;
        }
    }

    // sendto(bytes, address)
    // sendto(bytes, flags, address)
    @Builtin(name = "sendto", minNumOfPositionalArgs = 3, maxNumOfPositionalArgs = 4)
    @GenerateNodeFactory
    abstract static class SendToNode extends PythonBuiltinNode {
        @Specialization(limit = "3")
        Object sendTo(VirtualFrame frame, PSocket socket, Object bufferObj, Object flagsOrAddress, Object maybeAddress,
                        @CachedLibrary("bufferObj") PythonBufferAcquireLibrary bufferAcquireLib,
                        @CachedLibrary(limit = "1") PythonBufferAccessLibrary bufferLib,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached ConditionProfile hasFlagsProfile,
                        @Cached PyLongAsIntNode asIntNode,
                        @Cached SocketNodes.GetSockAddrArgNode getSockAddrArgNode,
                        @Cached SysModuleBuiltins.AuditNode auditNode,
                        @Cached GilNode gil) {
            int flags;
            Object address;
            if (hasFlagsProfile.profile(maybeAddress == PNone.NO_VALUE)) {
                address = flagsOrAddress;
                flags = 0;
            } else {
                address = maybeAddress;
                flags = asIntNode.execute(frame, flagsOrAddress);
            }

            Object buffer = bufferAcquireLib.acquireReadonly(bufferObj, frame, this);
            try {
                checkSelectable(this, socket);

                UniversalSockAddr addr = getSockAddrArgNode.execute(frame, socket, address, "sendto");
                auditNode.audit("socket.sendto", socket, address);

                int len = bufferLib.getBufferLength(buffer);
                byte[] bytes = bufferLib.getInternalOrCopiedByteArray(buffer);

                try {
                    return SocketUtils.callSocketFunctionWithRetry(frame, getConstructAndRaiseNode(), posixLib, getPosixSupport(), gil, socket,
                                    () -> posixLib.sendto(getPosixSupport(), socket.getFd(), bytes, 0, len, flags, addr),
                                    true, false);
                } catch (PosixException e) {
                    throw raiseOSErrorFromPosixException(frame, e);
                }
            } finally {
                bufferLib.release(buffer, frame, this);
            }
        }
    }

    @Builtin(name = "setblocking", minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 2, parameterNames = {"$self", "blocking"})
    @ArgumentClinic(name = "blocking", conversion = ArgumentClinic.ClinicConversion.Boolean)
    @GenerateNodeFactory
    public abstract static class SetBlockingNode extends PythonBinaryClinicBuiltinNode {
        @Specialization
        PNone doBoolean(VirtualFrame frame, PSocket socket, boolean blocking,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            try {
                posixLib.setBlocking(getPosixSupport(), socket.getFd(), blocking);
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e);
            }
            socket.setTimeoutNs(blocking ? -1 : 0);
            return PNone.NONE;
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return SocketBuiltinsClinicProviders.SetBlockingNodeClinicProviderGen.INSTANCE;
        }
    }

    // settimeout(value)
    @Builtin(name = "settimeout", minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class SetTimeoutNode extends PythonBinaryBuiltinNode {
        @Specialization
        Object setTimeout(VirtualFrame frame, PSocket socket, Object seconds,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached SocketNodes.ParseTimeoutNode parseTimeoutNode) {
            long timeout = parseTimeoutNode.execute(frame, seconds);
            socket.setTimeoutNs(timeout);
            try {
                posixLib.setBlocking(getPosixSupport(), socket.getFd(), timeout < 0);
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e);
            }
            return PNone.NONE;
        }
    }

    // shutdown(how)
    @Builtin(name = "shutdown", minNumOfPositionalArgs = 2, numOfPositionalOnlyArgs = 2, parameterNames = {"$self", "how"})
    @ArgumentClinic(name = "how", conversion = ArgumentClinic.ClinicConversion.Int)
    @GenerateNodeFactory
    abstract static class ShutdownNode extends PythonBinaryClinicBuiltinNode {
        @Specialization
        Object shutdown(VirtualFrame frame, PSocket socket, int how,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            try {
                posixLib.shutdown(getPosixSupport(), socket.getFd(), how);
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e);
            }
            return PNone.NONE;
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return SocketBuiltinsClinicProviders.ShutdownNodeClinicProviderGen.INSTANCE;
        }
    }

    // family
    @Builtin(name = "family", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class SocketFamilyNode extends PythonUnaryBuiltinNode {
        @Specialization
        int family(PSocket socket) {
            return socket.getFamily();
        }
    }

    // type
    @Builtin(name = "type", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class SocketTypeNode extends PythonUnaryBuiltinNode {
        @Specialization
        int type(PSocket socket) {
            return socket.getType();
        }
    }

    // proto
    @Builtin(name = "proto", minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class SockProtoNode extends PythonUnaryBuiltinNode {
        @Specialization
        int proto(PSocket socket) {
            return socket.getProto();
        }
    }

    // fileno
    @Builtin(name = "fileno", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class SockFilenoNode extends PythonUnaryBuiltinNode {
        @Specialization
        int fileno(PSocket socket) {
            return socket.getFd();
        }
    }

    // detach
    @Builtin(name = "detach", minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class SockDetachNode extends PythonUnaryBuiltinNode {
        @Specialization
        int detach(PSocket socket) {
            int fd = socket.getFd();
            socket.setFd(INVALID_FD);
            return fd;
        }
    }

    @Builtin(name = "setsockopt", minNumOfPositionalArgs = 4, numOfPositionalOnlyArgs = 5, parameterNames = {"$self", "level", "optname", "flag1", "flag2"})
    @ArgumentClinic(name = "level", conversion = ArgumentClinic.ClinicConversion.Int)
    @ArgumentClinic(name = "optname", conversion = ArgumentClinic.ClinicConversion.Int)
    @GenerateNodeFactory
    abstract static class SetSockOptNode extends PythonClinicBuiltinNode {
        @Specialization(guards = "isNoValue(none)")
        Object setInt(VirtualFrame frame, PSocket socket, int level, int option, Object value, @SuppressWarnings("unused") PNone none,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @CachedLibrary(limit = "3") PythonBufferAcquireLibrary bufferAcquireLib,
                        @CachedLibrary(limit = "3") PythonBufferAccessLibrary bufferLib,
                        @Cached PyLongAsIntNode asIntNode) {
            byte[] bytes;
            int len;
            try {
                int flag = asIntNode.execute(frame, value);
                bytes = new byte[4];
                len = bytes.length;
                PythonUtils.arrayAccessor.putInt(bytes, 0, flag);
            } catch (PException e) {
                Object buffer = bufferAcquireLib.acquireReadonly(value, frame, this);
                try {
                    len = bufferLib.getBufferLength(buffer);
                    bytes = bufferLib.getInternalOrCopiedByteArray(buffer);
                } finally {
                    bufferLib.release(buffer, frame, this);
                }

            }
            try {
                posixLib.setsockopt(getPosixSupport(), socket.getFd(), level, option, bytes, len);
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e);
            }
            return PNone.NONE;
        }

        @Specialization(guards = "isNone(none)")
        Object setNull(VirtualFrame frame, PSocket socket, int level, int option, @SuppressWarnings("unused") PNone none, Object buflenObj,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib,
                        @Cached PyLongAsIntNode asIntNode) {
            int buflen = asIntNode.execute(frame, buflenObj);
            if (buflen < 0) {
                // GraalPython-specific because we don't have unsigned integers
                throw raise(OSError, ErrorMessages.SETSECKOPT_BUFF_OUT_OFRANGE);
            }
            try {
                posixLib.setsockopt(getPosixSupport(), socket.getFd(), level, option, null, buflen);
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e);
            }
            return PNone.NONE;
        }

        @Fallback
        @SuppressWarnings("unused")
        Object error(Object self, Object level, Object option, Object flag1, Object flag2) {
            throw raise(TypeError, ErrorMessages.SETSECKOPT_REQUIRERS_3RD_ARG_NULL);
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return SocketBuiltinsClinicProviders.SetSockOptNodeClinicProviderGen.INSTANCE;
        }
    }

    @Builtin(name = "getsockopt", minNumOfPositionalArgs = 3, numOfPositionalOnlyArgs = 4, parameterNames = {"$self", "level", "optname", "buflen"})
    @ArgumentClinic(name = "level", conversion = ArgumentClinic.ClinicConversion.Int)
    @ArgumentClinic(name = "optname", conversion = ArgumentClinic.ClinicConversion.Int)
    @ArgumentClinic(name = "buflen", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "0")
    @GenerateNodeFactory
    abstract static class GetSockOptNode extends PythonQuaternaryClinicBuiltinNode {
        @Specialization
        Object getSockOpt(VirtualFrame frame, PSocket socket, int level, int option, int buflen,
                        @CachedLibrary("getPosixSupport()") PosixSupportLibrary posixLib) {
            try {
                if (buflen == 0) {
                    byte[] result = new byte[4];
                    posixLib.getsockopt(getPosixSupport(), socket.getFd(), level, option, result, result.length);
                    return PythonUtils.arrayAccessor.getInt(result, 0);
                } else if (buflen > 0 && buflen < 1024) {
                    byte[] result = new byte[buflen];
                    int len = posixLib.getsockopt(getPosixSupport(), socket.getFd(), level, option, result, result.length);
                    return factory().createBytes(result, len);
                } else {
                    throw raise(OSError, ErrorMessages.GETSECKOPT_BUFF_OUT_OFRANGE);
                }
            } catch (PosixException e) {
                throw raiseOSErrorFromPosixException(frame, e);
            }
        }

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return SocketBuiltinsClinicProviders.GetSockOptNodeClinicProviderGen.INSTANCE;
        }
    }
}

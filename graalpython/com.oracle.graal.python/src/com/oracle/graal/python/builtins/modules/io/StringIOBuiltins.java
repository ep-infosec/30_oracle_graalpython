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
package com.oracle.graal.python.builtins.modules.io;

import static com.oracle.graal.python.builtins.PythonBuiltinClassType.PIncrementalNewlineDecoder;
import static com.oracle.graal.python.builtins.modules.io.BufferedIOUtil.SEEK_CUR;
import static com.oracle.graal.python.builtins.modules.io.BufferedIOUtil.SEEK_END;
import static com.oracle.graal.python.builtins.modules.io.BufferedIOUtil.SEEK_SET;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_CLOSE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_CLOSED;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_GETVALUE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_LINE_BUFFERING;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_NEWLINES;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_READ;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_READABLE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_READLINE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_SEEK;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_SEEKABLE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_TELL;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_TRUNCATE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_WRITABLE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.J_WRITE;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_NEWLINES;
import static com.oracle.graal.python.builtins.modules.io.IONodes.T_READLINE;
import static com.oracle.graal.python.builtins.modules.io.TextIOWrapperNodes.validateNewline;
import static com.oracle.graal.python.nodes.ErrorMessages.CAN_T_DO_NONZERO_CUR_RELATIVE_SEEKS;
import static com.oracle.graal.python.nodes.ErrorMessages.INVALID_WHENCE_D_SHOULD_BE_0_1_OR_2;
import static com.oracle.graal.python.nodes.ErrorMessages.IO_CLOSED;
import static com.oracle.graal.python.nodes.ErrorMessages.IO_UNINIT;
import static com.oracle.graal.python.nodes.ErrorMessages.NEGATIVE_SEEK_VALUE_D;
import static com.oracle.graal.python.nodes.ErrorMessages.NEGATIVE_SIZE_VALUE_D;
import static com.oracle.graal.python.nodes.ErrorMessages.NEW_POSITION_TOO_LARGE;
import static com.oracle.graal.python.nodes.ErrorMessages.POSITION_VALUE_CANNOT_BE_NEGATIVE;
import static com.oracle.graal.python.nodes.ErrorMessages.P_SETSTATE_ARGUMENT_SHOULD_BE_D_TUPLE_GOT_P;
import static com.oracle.graal.python.nodes.ErrorMessages.S_SHOULD_HAVE_RETURNED_A_STR_OBJECT_NOT_P;
import static com.oracle.graal.python.nodes.ErrorMessages.THIRD_ITEM_OF_STATE_MUST_BE_AN_INTEGER_GOT_P;
import static com.oracle.graal.python.nodes.ErrorMessages.THIRD_ITEM_OF_STATE_SHOULD_BE_A_DICT_GOT_A_P;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___GETSTATE__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___INIT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___NEXT__;
import static com.oracle.graal.python.nodes.SpecialMethodNames.J___SETSTATE__;
import static com.oracle.graal.python.nodes.StringLiterals.T_EMPTY_STRING;
import static com.oracle.graal.python.nodes.StringLiterals.T_NEWLINE;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.OSError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.OverflowError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.TypeError;
import static com.oracle.graal.python.runtime.exception.PythonErrorType.ValueError;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;

import java.util.List;

import com.oracle.graal.python.annotations.ArgumentClinic;
import com.oracle.graal.python.builtins.Builtin;
import com.oracle.graal.python.builtins.CoreFunctions;
import com.oracle.graal.python.builtins.PythonBuiltinClassType;
import com.oracle.graal.python.builtins.PythonBuiltins;
import com.oracle.graal.python.builtins.modules.io.TextIOWrapperNodes.FindLineEndingNode;
import com.oracle.graal.python.builtins.objects.PNone;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageAddAllToOther;
import com.oracle.graal.python.builtins.objects.common.SequenceStorageNodes;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.str.StringNodes.StringReplaceNode;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.lib.PyIndexCheckNode;
import com.oracle.graal.python.lib.PyNumberAsSizeNode;
import com.oracle.graal.python.lib.PyNumberIndexNode;
import com.oracle.graal.python.lib.PyObjectCallMethodObjArgs;
import com.oracle.graal.python.lib.PyObjectGetAttr;
import com.oracle.graal.python.nodes.PGuards;
import com.oracle.graal.python.nodes.PRaiseNode;
import com.oracle.graal.python.nodes.function.PythonBuiltinBaseNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonBinaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonTernaryClinicBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.PythonUnaryBuiltinNode;
import com.oracle.graal.python.nodes.function.builtins.clinic.ArgumentClinicProvider;
import com.oracle.graal.python.nodes.object.GetOrCreateDictNode;
import com.oracle.graal.python.nodes.object.IsBuiltinClassProfile;
import com.oracle.graal.python.nodes.truffle.PythonArithmeticTypes;
import com.oracle.graal.python.nodes.util.CastToTruffleStringNode;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Cached.Shared;
import com.oracle.truffle.api.dsl.GenerateNodeFactory;
import com.oracle.truffle.api.dsl.NodeFactory;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.TypeSystemReference;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleStringBuilder;

@CoreFunctions(extendClasses = PythonBuiltinClassType.PStringIO)
public final class StringIOBuiltins extends PythonBuiltins {

    private static final TruffleString T_NIL = TruffleString.fromCodePointUncached(0, TS_ENCODING);

    @Override
    protected List<? extends NodeFactory<? extends PythonBuiltinBaseNode>> getNodeFactories() {
        return StringIOBuiltinsFactory.getFactories();
    }

    abstract static class ClosedCheckPythonUnaryBuiltinNode extends PythonUnaryBuiltinNode {
        @Specialization(guards = "!self.isOK()")
        Object initError(@SuppressWarnings("unused") PStringIO self) {
            throw raise(ValueError, IO_UNINIT);
        }

        @Specialization(guards = "self.isClosed()")
        Object closedError(@SuppressWarnings("unused") PStringIO self) {
            throw raise(ValueError, IO_CLOSED);
        }
    }

    abstract static class ClosedCheckPythonBinaryClinicBuiltinNode extends PythonBinaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            throw CompilerDirectives.shouldNotReachHere();
        }

        @Specialization(guards = "!self.isOK()")
        Object initError(@SuppressWarnings("unused") PStringIO self, @SuppressWarnings("unused") Object arg) {
            throw raise(ValueError, IO_UNINIT);
        }

        @Specialization(guards = "self.isClosed()")
        Object closedError(@SuppressWarnings("unused") PStringIO self, @SuppressWarnings("unused") Object arg) {
            throw raise(ValueError, IO_CLOSED);
        }
    }

    static void writeString(VirtualFrame frame, PStringIO self, TruffleString obj,
                    PRaiseNode raise,
                    IncrementalNewlineDecoderBuiltins.DecodeNode decodeNode,
                    StringReplaceNode replaceNode,
                    TruffleString.CodePointLengthNode codePointLengthNode,
                    TruffleString.RepeatNode repeatNode,
                    TruffleString.SubstringNode substringNode,
                    TruffleStringBuilder.AppendStringNode appendStringNode,
                    TruffleStringBuilder.ToStringNode toStringNode) {
        assert (self.getPos() >= 0);
        TruffleString decoded;
        if (self.getDecoder() != null) {
            // (mq) IncrementalNewlineDecoderBuiltins.DecodeNode always returns a TruffleString
            decoded = (TruffleString) decodeNode.execute(frame, self.getDecoder(), obj, true /*- always final */);
        } else {
            decoded = obj;
        }
        if (self.hasWriteNewline()) {
            decoded = replaceNode.execute(decoded, T_NEWLINE, self.getWriteNewline(), -1);
        }

        int len = codePointLengthNode.execute(decoded, TS_ENCODING);

        /*
         * This overflow check is not strictly necessary. However, it avoids us to deal with funky
         * things like comparing an unsigned and a signed integer.
         */
        if (self.getPos() > Integer.MAX_VALUE - len) {
            throw raise.raise(OverflowError, NEW_POSITION_TOO_LARGE);
        }

        if (self.isAccumulating()) {
            if (self.getStringSize() == self.getPos()) {
                self.append(decoded, appendStringNode);
                self.incPos(len);
                if (self.getStringSize() < self.getPos()) {
                    self.setStringsize(self.getPos());
                }
                return;
            }
            self.realize(toStringNode);
        }

        TruffleStringBuilder sb = TruffleStringBuilder.create(TS_ENCODING);
        if (self.getPos() > self.getStringSize()) {
            /*
             * In case of overseek, pad with null bytes the buffer region between the end of stream
             * and the current position.
             */
            appendStringNode.execute(sb, self.getBuf());
            // TODO GR-37214: append repeated codepoint
            TruffleString nil = repeatNode.execute(T_NIL, self.getPos() - self.getStringSize(), TS_ENCODING);
            appendStringNode.execute(sb, nil);
            appendStringNode.execute(sb, decoded);
        } else if (self.getPos() < self.getStringSize()) {
            /*
             * Copy the data to the internal buffer, overwriting some of the existing data if
             * self.getPos() < self.getStringSize().
             */
            TruffleString left = substringNode.execute(self.getBuf(), 0, self.getPos(), TS_ENCODING, true);
            appendStringNode.execute(sb, left);
            appendStringNode.execute(sb, decoded);
            int end = self.getPos() + len;
            if (end < self.getStringSize()) {
                TruffleString right = substringNode.execute(self.getBuf(), end, self.getStringSize() - end, TS_ENCODING, true);
                appendStringNode.execute(sb, right);
            }
        } else {
            appendStringNode.execute(sb, self.getBuf());
            appendStringNode.execute(sb, decoded);
        }
        self.setBuf(toStringNode.execute(sb));

        /* Set the new length of the internal string if it has changed. */
        self.incPos(len);
        if (self.getStringSize() < self.getPos()) {
            self.setStringsize(self.getPos());
        }
    }

    @Builtin(name = J___INIT__, minNumOfPositionalArgs = 1, parameterNames = {"$self", "initial_value", "newline"})
    @ArgumentClinic(name = "initial_value", conversion = ArgumentClinic.ClinicConversion.TString, defaultValue = "T_EMPTY_STRING", useDefaultForNone = true)
    @GenerateNodeFactory
    public abstract static class InitNode extends PythonTernaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return StringIOBuiltinsClinicProviders.InitNodeClinicProviderGen.INSTANCE;
        }

        @Specialization
        PNone init(VirtualFrame frame, PStringIO self, TruffleString initialValue, Object newlineArg,
                        @Cached IncrementalNewlineDecoderBuiltins.DecodeNode decodeNode,
                        @Cached IncrementalNewlineDecoderBuiltins.InitNode initNode,
                        @Cached StringReplaceNode replaceNode,
                        @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Cached TruffleString.CodePointAtIndexNode codePointAtIndexNode,
                        @Cached TruffleString.RepeatNode repeatNode,
                        @Cached TruffleString.SubstringNode substringNode,
                        @Cached TruffleStringBuilder.AppendStringNode appendStringNode,
                        @Cached TruffleStringBuilder.ToStringNode toStringNode,
                        @Cached IONodes.ToTruffleStringNode toTruffleStringNode) {
            TruffleString newline;

            if (newlineArg == PNone.NO_VALUE) {
                newline = T_NEWLINE;
            } else if (newlineArg == PNone.NONE) {
                newline = null;
            } else {
                newline = toTruffleStringNode.execute(newlineArg);
            }

            if (newline != null) {
                validateNewline(newline, getRaiseNode(), codePointLengthNode, codePointAtIndexNode);
            }
            self.setOK(false);
            self.clearAll();

            if (newline != null) {
                self.setReadNewline(newline);
            }
            self.setReadUniversal(newline == null || newline.isEmpty() || codePointAtIndexNode.execute(newline, 0, TS_ENCODING) == '\0');
            self.setReadTranslate(newline == null);
            /*-
                If newline == "", we don't translate anything.
                If newline == "\n" or newline == None, we translate to "\n", which is a no-op.
                (for newline == None, TextIOWrapper translates to os.linesep, but it
                is pointless for StringIO)
            */
            if (newline != null && !newline.isEmpty() && codePointAtIndexNode.execute(newline, 0, TS_ENCODING) == '\r') {
                self.setWriteNewline(self.getReadNewline());
            }

            if (self.isReadUniversal()) {
                Object incDecoder = factory().createNLDecoder(PIncrementalNewlineDecoder);
                initNode.execute(frame, incDecoder, self.getDecoder(), self.isReadTranslate(), PNone.NO_VALUE);
                self.setDecoder(incDecoder);
            }

            /*
             * Now everything is set up, resize buffer to size of initial value, and copy it
             */
            self.setStringsize(0);
            if (!initialValue.isEmpty()) {
                self.setRealized();
                self.setPos(0);
                writeString(frame, self, initialValue, getRaiseNode(), decodeNode, replaceNode, codePointLengthNode, repeatNode, substringNode, appendStringNode, toStringNode);
            } else {
                /* Empty stringio object, we can start by accumulating */
                self.setAccumulating();
            }
            self.setPos(0);

            self.setClosed(false);
            self.setOK(true);
            return PNone.NONE;
        }
    }

    @Builtin(name = J_READ, minNumOfPositionalArgs = 1, parameterNames = {"$self", "size"})
    @ArgumentClinic(name = "size", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "-1", useDefaultForNone = true)
    @GenerateNodeFactory
    abstract static class ReadNode extends ClosedCheckPythonBinaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return StringIOBuiltinsClinicProviders.ReadNodeClinicProviderGen.INSTANCE;
        }

        @Specialization(guards = {"self.isOK()", "!self.isClosed()"})
        static TruffleString read(PStringIO self, int len,
                        @Cached TruffleString.SubstringNode substringNode,
                        @Cached TruffleStringBuilder.ToStringNode toStringNode) {
            int size = len;
            /* adjust invalid sizes */
            int n = self.getStringSize() - self.getPos();
            if (size < 0 || size > n) {
                size = n;
                if (size < 0) {
                    size = 0;
                }
            }

            if (size == 0) {
                return T_EMPTY_STRING;
            }

            if (self.isAccumulating() && self.getPos() == 0 && size == n) {
                self.setPos(self.getStringSize());
                return self.makeIntermediate(toStringNode);
            }

            self.realize(toStringNode);
            int newPos = self.getPos() + size;
            TruffleString output = substringNode.execute(self.getBuf(), self.getPos(), size, TS_ENCODING, false);
            self.setPos(newPos);
            return output;
        }
    }

    static TruffleString stringioReadline(PStringIO self, int lim, FindLineEndingNode findLineEndingNode, TruffleString.SubstringNode substringNode) {
        /* In case of overseek, return the empty string */
        if (self.getPos() >= self.getStringSize()) {
            return T_EMPTY_STRING;
        }

        int limit = lim;
        int start = self.getPos();
        if (limit < 0 || limit > self.getStringSize() - self.getPos()) {
            limit = self.getStringSize() - self.getPos();
        }

        int[] consumed = new int[1];
        int len = findLineEndingNode.execute(self, self.getBuf(), start, consumed);
        /*
         * If we haven't found any line ending, we just return everything (`consumed` is ignored).
         */
        if (len < 0 || len > limit) {
            len = limit;
        }
        self.incPos(len);
        return substringNode.execute(self.getBuf(), start, len, TS_ENCODING, false);
    }

    @Builtin(name = J_READLINE, minNumOfPositionalArgs = 1, parameterNames = {"$self", "size"})
    @ArgumentClinic(name = "size", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "-1", useDefaultForNone = true)
    @GenerateNodeFactory
    abstract static class ReadlineNode extends ClosedCheckPythonBinaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return StringIOBuiltinsClinicProviders.ReadlineNodeClinicProviderGen.INSTANCE;
        }

        @Specialization(guards = {"self.isOK()", "!self.isClosed()"})
        static TruffleString readline(PStringIO self, int size,
                        @Cached FindLineEndingNode findLineEndingNode,
                        @Cached TruffleString.SubstringNode substringNode,
                        @Cached TruffleStringBuilder.ToStringNode toStringNode) {
            self.realize(toStringNode);
            return stringioReadline(self, size, findLineEndingNode, substringNode);
        }
    }

    @Builtin(name = J_TRUNCATE, minNumOfPositionalArgs = 1, parameterNames = {"$self", "size"})
    @ArgumentClinic(name = "size", defaultValue = "PNone.NONE", useDefaultForNone = true)
    @TypeSystemReference(PythonArithmeticTypes.class)
    @GenerateNodeFactory
    abstract static class TruncateNode extends ClosedCheckPythonBinaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return StringIOBuiltinsClinicProviders.TruncateNodeClinicProviderGen.INSTANCE;
        }

        @Specialization(guards = {"self.isOK()", "!self.isClosed()"})
        static Object truncate(PStringIO self, @SuppressWarnings("unused") PNone size,
                        @Cached TruffleString.SubstringNode substringNode,
                        @Cached TruffleStringBuilder.ToStringNode toStringNode) {
            return truncate(self, self.getPos(), substringNode, toStringNode);
        }

        @Specialization(guards = {"self.isOK()", "!self.isClosed()", "size >= 0", "size < self.getStringSize()"})
        static Object truncate(PStringIO self, int size,
                        @Cached TruffleString.SubstringNode substringNode,
                        @Cached TruffleStringBuilder.ToStringNode toStringNode) {
            self.realize(toStringNode);
            self.setBuf(substringNode.execute(self.getBuf(), 0, size, TS_ENCODING, false));
            self.setStringsize(size);
            return size;
        }

        @Specialization(guards = {"self.isOK()", "!self.isClosed()", "size >= 0", "size >= self.getStringSize()"})
        static Object same(@SuppressWarnings("unused") PStringIO self, int size) {
            return size;
        }

        @Specialization(guards = {"self.isOK()", "!self.isClosed()", "!isInteger(arg)", "!isPNone(arg)"})
        Object obj(VirtualFrame frame, PStringIO self, Object arg,
                        @Cached PyNumberAsSizeNode asSizeNode,
                        @Cached PyNumberIndexNode indexNode,
                        @Cached TruffleString.SubstringNode substringNode,
                        @Cached TruffleStringBuilder.ToStringNode toStringNode) {
            int size = asSizeNode.executeExact(frame, indexNode.execute(frame, arg), OverflowError);
            if (size >= 0) {
                if (size < self.getStringSize()) {
                    return truncate(self, size, substringNode, toStringNode);
                }
                return size;
            }
            return negSize(self, size);
        }

        @Specialization(guards = {"self.isOK()", "!self.isClosed()", "size < 0"})
        Object negSize(@SuppressWarnings("unused") PStringIO self, int size) {
            throw raise(ValueError, NEGATIVE_SIZE_VALUE_D, size);
        }
    }

    @Builtin(name = J_WRITE, minNumOfPositionalArgs = 2, parameterNames = {"self", "s"})
    @ArgumentClinic(name = "s", conversion = ArgumentClinic.ClinicConversion.TString)
    @GenerateNodeFactory
    abstract static class WriteNode extends ClosedCheckPythonBinaryClinicBuiltinNode {

        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return StringIOBuiltinsClinicProviders.WriteNodeClinicProviderGen.INSTANCE;
        }

        @Specialization(guards = {"self.isOK()", "!self.isClosed()"})
        Object doWrite(VirtualFrame frame, PStringIO self, TruffleString s,
                        @Cached IncrementalNewlineDecoderBuiltins.DecodeNode decodeNode,
                        @Cached StringReplaceNode replaceNode,
                        @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Cached TruffleString.RepeatNode repeatNode,
                        @Cached TruffleString.SubstringNode substringNode,
                        @Cached TruffleStringBuilder.AppendStringNode appendStringNode,
                        @Cached TruffleStringBuilder.ToStringNode toStringNode) {
            int size = codePointLengthNode.execute(s, TS_ENCODING);
            if (size > 0) {
                writeString(frame, self, s, getRaiseNode(), decodeNode, replaceNode, codePointLengthNode, repeatNode, substringNode, appendStringNode, toStringNode);
            }
            return size;
        }
    }

    @Builtin(name = J_TELL, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class TellNode extends ClosedCheckPythonUnaryBuiltinNode {

        @Specialization(guards = {"self.isOK()", "!self.isClosed()"})
        static Object tell(PStringIO self) {
            return self.getPos();
        }
    }

    @Builtin(name = J_SEEK, minNumOfPositionalArgs = 2, parameterNames = {"$self", "pos", "whence"})
    @ArgumentClinic(name = "pos", conversion = ArgumentClinic.ClinicConversion.Index)
    @ArgumentClinic(name = "whence", conversion = ArgumentClinic.ClinicConversion.Int, defaultValue = "BufferedIOUtil.SEEK_SET", useDefaultForNone = true)
    @GenerateNodeFactory
    abstract static class SeekNode extends PythonTernaryClinicBuiltinNode {
        @Override
        protected ArgumentClinicProvider getArgumentClinic() {
            return StringIOBuiltinsClinicProviders.SeekNodeClinicProviderGen.INSTANCE;
        }

        protected static boolean isSupportedWhence(int whence) {
            return whence == SEEK_SET || whence == SEEK_CUR || whence == SEEK_END;
        }

        protected static boolean validPos(int pos, int whence) {
            return !(pos < 0 && whence == 0) && !(pos != 0 && whence != 0);
        }

        @Specialization(guards = {"self.isOK()", "!self.isClosed()", "isSupportedWhence(whence)", "validPos(pos, whence)"})
        static Object seek(PStringIO self, int pos, int whence) {
            int p = pos;
            /*-
               whence = 0: offset relative to beginning of the string.
               whence = 1: no change to current position.
               whence = 2: change position to end of file.
            */
            if (whence == 1) {
                p = self.getPos();
            } else if (whence == 2) {
                p = self.getStringSize();
            }

            self.setPos(p);
            return self.getPos();
        }

        @SuppressWarnings("unused")
        @Specialization(guards = {"self.isOK()", "!self.isClosed()", "!isSupportedWhence(whence)"})
        Object whenceError(@SuppressWarnings("unused") PStringIO self, @SuppressWarnings("unused") int pos, int whence) {
            throw raise(ValueError, INVALID_WHENCE_D_SHOULD_BE_0_1_OR_2, whence);
        }

        @Specialization(guards = {"self.isOK()", "!self.isClosed()", "isSupportedWhence(whence)", "pos != 0", "whence != 0"})
        Object largePos1(@SuppressWarnings("unused") PStringIO self, @SuppressWarnings("unused") int pos, @SuppressWarnings("unused") int whence) {
            throw raise(OSError, CAN_T_DO_NONZERO_CUR_RELATIVE_SEEKS);
        }

        @Specialization(guards = {"self.isOK()", "!self.isClosed()", "pos < 0", "whence == 0"})
        Object negPos(@SuppressWarnings("unused") PStringIO self, int pos, @SuppressWarnings("unused") int whence) {
            throw raise(ValueError, NEGATIVE_SEEK_VALUE_D, pos);
        }

        @Specialization(guards = "!self.isOK()")
        Object initError(@SuppressWarnings("unused") PStringIO self, @SuppressWarnings("unused") int pos, @SuppressWarnings("unused") int whence) {
            throw raise(ValueError, IO_UNINIT);
        }

        @Specialization(guards = "self.isClosed()")
        Object closedError(@SuppressWarnings("unused") PStringIO self, @SuppressWarnings("unused") int pos, @SuppressWarnings("unused") int whence) {
            throw raise(ValueError, IO_CLOSED);
        }
    }

    @Builtin(name = J_GETVALUE, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class GetValueNode extends ClosedCheckPythonUnaryBuiltinNode {

        @Specialization(guards = {"self.isOK()", "!self.isClosed()", "self.isAccumulating()"})
        static Object copy(PStringIO self,
                        @Cached TruffleStringBuilder.ToStringNode toStringNode) {
            return self.makeIntermediate(toStringNode);
        }

        @Specialization(guards = {"self.isOK()", "!self.isClosed()", "!self.isAccumulating()"})
        static Object doit(PStringIO self) {
            return self.getBuf();
        }
    }

    @Builtin(name = J___GETSTATE__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class GetStateNode extends ClosedCheckPythonUnaryBuiltinNode {

        @Specialization(guards = {"self.isOK()", "!self.isClosed()"})
        Object doit(VirtualFrame frame, PStringIO self,
                        @Cached GetValueNode getValueNode,
                        @Cached GetOrCreateDictNode getDict) {
            Object initValue = getValueNode.execute(frame, self);
            Object readnl = self.getReadNewline() == null ? PNone.NONE : self.getReadNewline();
            Object[] state = new Object[]{initValue, readnl, self.getPos(), getDict.execute(self)};
            return factory().createTuple(state);
        }
    }

    @Builtin(name = J___SETSTATE__, minNumOfPositionalArgs = 2)
    @GenerateNodeFactory
    abstract static class SetStateNode extends PythonBinaryBuiltinNode {

        @Specialization(guards = {"!self.isClosed()"})
        Object doit(VirtualFrame frame, PStringIO self, PTuple state,
                        @Cached SequenceStorageNodes.GetInternalObjectArrayNode getArray,
                        @Cached InitNode initNode,
                        @Cached CastToTruffleStringNode toString,
                        @Cached PyIndexCheckNode indexCheckNode,
                        @Cached PyNumberAsSizeNode asSizeNode,
                        @Cached GetOrCreateDictNode getDict,
                        @Cached TruffleString.CodePointLengthNode codePointLengthNode,
                        @Cached HashingStorageAddAllToOther addAllToOtherNode) {
            Object[] array = getArray.execute(state.getSequenceStorage());
            if (array.length < 4) {
                return notTuple(self, state);
            }
            initNode.execute(frame, self, array[0], array[1]);
            /*
             * Restore the buffer state. Even if __init__ did initialize the buffer, we have to
             * initialize it again since __init__ may translate the newlines in the initial_value
             * string. We clearly do not want that because the string value in the state tuple has
             * already been translated once by __init__. So we do not take any chance and replace
             * object's buffer completely.
             */

            TruffleString buf = toString.execute(array[0]);
            int bufsize = codePointLengthNode.execute(buf, TS_ENCODING);
            self.setRealized();
            self.setBuf(buf);
            self.setStringsize(bufsize);

            /*
             * Set carefully the position value. Alternatively, we could use the seek method instead
             * of modifying self->pos directly to better protect the object internal state against
             * erroneous (or malicious) inputs.
             */
            if (!indexCheckNode.execute(array[2])) {
                throw raise(TypeError, THIRD_ITEM_OF_STATE_MUST_BE_AN_INTEGER_GOT_P, array[2]);
            }
            int pos = asSizeNode.executeExact(frame, array[2]);
            if (pos < 0) {
                throw raise(ValueError, POSITION_VALUE_CANNOT_BE_NEGATIVE);
            }
            self.setPos(pos);

            /* Set the dictionary of the instance variables. */
            if (!PGuards.isNone(array[3])) {
                if (!PGuards.isDict(array[3])) {
                    throw raise(TypeError, THIRD_ITEM_OF_STATE_SHOULD_BE_A_DICT_GOT_A_P, array[3]);
                }
                /*
                 * Alternatively, we could replace the internal dictionary completely. However, it
                 * seems more practical to just update it.
                 */
                PDict dict = getDict.execute(self);
                addAllToOtherNode.execute(frame, ((PDict) array[3]).getDictStorage(), dict.getDictStorage());
            }

            return PNone.NONE;
        }

        @Specialization(guards = {"!self.isClosed()", "!isPTuple(state)"})
        Object notTuple(PStringIO self, Object state) {
            throw raise(TypeError, P_SETSTATE_ARGUMENT_SHOULD_BE_D_TUPLE_GOT_P, self, 4, state);
        }

        @Specialization(guards = "self.isClosed()")
        Object closedError(@SuppressWarnings("unused") PStringIO self, @SuppressWarnings("unused") Object arg) {
            throw raise(ValueError, IO_CLOSED);
        }
    }

    @Builtin(name = J_SEEKABLE, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class SeekableNode extends ClosedCheckPythonUnaryBuiltinNode {

        @Specialization(guards = {"self.isOK()", "!self.isClosed()"})
        static boolean seekable(@SuppressWarnings("unused") PStringIO self) {
            return true;
        }
    }

    @Builtin(name = J_READABLE, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class ReadableNode extends ClosedCheckPythonUnaryBuiltinNode {

        @Specialization(guards = {"self.isOK()", "!self.isClosed()"})
        static boolean readable(@SuppressWarnings("unused") PStringIO self) {
            return true;
        }
    }

    @Builtin(name = J_WRITABLE, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class WritableNode extends ClosedCheckPythonUnaryBuiltinNode {

        @Specialization(guards = {"self.isOK()", "!self.isClosed()"})
        static boolean writable(@SuppressWarnings("unused") PStringIO self) {
            return true;
        }
    }

    @Builtin(name = J_LINE_BUFFERING, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class LineBufferingNode extends ClosedCheckPythonUnaryBuiltinNode {
        @Specialization(guards = {"self.isOK()", "!self.isClosed()"})
        static Object lineBuffering(@SuppressWarnings("unused") PStringIO self) {
            return false;
        }
    }

    @Builtin(name = J_NEWLINES, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class NewlinesNode extends ClosedCheckPythonUnaryBuiltinNode {
        @Specialization(guards = {"self.isOK()", "!self.isClosed()", "!self.hasDecoder()"})
        static Object none(@SuppressWarnings("unused") PStringIO self) {
            return PNone.NONE;
        }

        @Specialization(guards = {"self.isOK()", "!self.isClosed()", "self.hasDecoder()"})
        static Object doit(VirtualFrame frame, PStringIO self,
                        @Cached PyObjectGetAttr getAttr) {
            return getAttr.execute(frame, self.getDecoder(), T_NEWLINES);
        }
    }

    @Builtin(name = J_CLOSED, minNumOfPositionalArgs = 1, isGetter = true)
    @GenerateNodeFactory
    abstract static class ClosedNode extends PythonUnaryBuiltinNode {

        @Specialization(guards = {"self.isOK()"})
        static boolean closed(PStringIO self) {
            return self.isClosed();
        }

        @Specialization(guards = "!self.isOK()")
        Object initError(@SuppressWarnings("unused") PStringIO self) {
            throw raise(ValueError, IO_UNINIT);
        }
    }

    @Builtin(name = J_CLOSE, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class CloseNode extends PythonUnaryBuiltinNode {

        @Specialization
        static Object close(PStringIO self) {
            self.setClosed(true);
            self.clearAll();
            return PNone.NONE;
        }
    }

    @Builtin(name = J___NEXT__, minNumOfPositionalArgs = 1)
    @GenerateNodeFactory
    abstract static class IternextNode extends ClosedCheckPythonUnaryBuiltinNode {

        protected static boolean isStringIO(PStringIO self, IsBuiltinClassProfile profile) {
            return profile.profileObject(self, PythonBuiltinClassType.PStringIO);
        }

        @Specialization(guards = {"self.isOK()", "!self.isClosed()", "isStringIO(self, profile)"}, limit = "1")
        Object builtin(PStringIO self,
                        @SuppressWarnings("unused") @Shared("profile") @Cached IsBuiltinClassProfile profile,
                        @Shared("sbToString") @Cached TruffleStringBuilder.ToStringNode toStringNode,
                        @Cached FindLineEndingNode findLineEndingNode,
                        @Cached TruffleString.SubstringNode substringNode) {
            self.realize(toStringNode);
            TruffleString line = stringioReadline(self, -1, findLineEndingNode, substringNode);
            if (line.isEmpty()) {
                throw raiseStopIteration();
            }
            return line;
        }

        /*
         * This path is rarely executed.
         */
        @Specialization(guards = {"self.isOK()", "!self.isClosed()", "!isStringIO(self, profile)"}, limit = "1")
        Object slowpath(VirtualFrame frame, PStringIO self,
                        @SuppressWarnings("unused") @Shared("profile") @Cached IsBuiltinClassProfile profile,
                        @Cached PyObjectCallMethodObjArgs callMethodReadline,
                        @Cached CastToTruffleStringNode toString,
                        @Shared("sbToString") @Cached TruffleStringBuilder.ToStringNode toStringNode) {
            self.realize(toStringNode);
            Object res = callMethodReadline.execute(frame, self, T_READLINE);
            if (!PGuards.isString(res)) {
                throw raise(OSError, S_SHOULD_HAVE_RETURNED_A_STR_OBJECT_NOT_P, T_READLINE, res);
            }
            TruffleString line = toString.execute(res);
            if (line.isEmpty()) {
                throw raiseStopIteration();
            }
            return line;
        }
    }
}

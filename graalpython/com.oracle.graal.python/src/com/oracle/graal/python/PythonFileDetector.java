/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.python;

import static com.oracle.graal.python.nodes.StringLiterals.J_PY_EXTENSION;
import static com.oracle.graal.python.nodes.StringLiterals.T_UTF_UNDERSCORE_8;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.oracle.graal.python.util.CharsetMapping;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.strings.TruffleString;

public final class PythonFileDetector implements TruffleFile.FileTypeDetector {

    private static final String UTF_8_BOM_IN_LATIN_1 = new String(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF}, StandardCharsets.ISO_8859_1);
    private static final Pattern ENCODING_COMMENT = Pattern.compile("^[ \t\f]*#.*?coding[:=][ \t]*([-_.a-zA-Z0-9]+).*");
    private static final Pattern BLANK_LINE = Pattern.compile("^[ \t\f]*(?:#.*)?");

    @Override
    public String findMimeType(TruffleFile file) throws IOException {
        String fileName = file.getName();
        if (fileName != null && fileName.endsWith(J_PY_EXTENSION)) {
            return PythonLanguage.MIME_TYPE;
        }
        return null;
    }

    public static class InvalidEncodingException extends RuntimeException {
        private static final long serialVersionUID = 1L;

        private final String encodingName;

        public InvalidEncodingException(String encodingName) {
            super("Invalid or unsupported encoding: " + encodingName);
            this.encodingName = encodingName;
        }

        public String getEncodingName() {
            return encodingName;
        }
    }

    private static Charset tryGetCharsetFromLine(String line, boolean hasBOM) {
        if (line == null) {
            return null;
        }
        Matcher matcher = ENCODING_COMMENT.matcher(line);
        if (matcher.matches()) {
            // Files with UTF-8 BOM but different encoding declared are a SyntaxError
            // Note that CPython ignores UTF-8 aliases for the BOM check
            String encoding = matcher.group(1);
            TruffleString normalizedEncoding = CharsetMapping.normalizeUncached(toTruffleStringUncached(encoding));
            if (hasBOM && !normalizedEncoding.equalsUncached(T_UTF_UNDERSCORE_8, TS_ENCODING)) {
                throw new InvalidEncodingException(encoding + " with BOM");
            }
            Charset charset = CharsetMapping.getCharsetNormalized(normalizedEncoding);
            if (charset == null) {
                throw new InvalidEncodingException(encoding);
            }
            return charset;
        }
        return null;
    }

    @TruffleBoundary
    public static Charset findEncodingStrict(BufferedReader reader) throws IOException {
        // Read first two lines like CPython
        String firstLine = reader.readLine();
        if (firstLine != null) {
            boolean hasBOM = false;
            if (firstLine.startsWith(UTF_8_BOM_IN_LATIN_1)) {
                hasBOM = true;
                firstLine = firstLine.substring(UTF_8_BOM_IN_LATIN_1.length());
            }
            Charset charset;
            if ((charset = tryGetCharsetFromLine(firstLine, hasBOM)) != null) {
                return charset;
            }
            if (BLANK_LINE.matcher(firstLine).matches()) {
                if ((charset = tryGetCharsetFromLine(reader.readLine(), hasBOM)) != null) {
                    return charset;
                }
            }
        }
        return StandardCharsets.UTF_8;
    }

    @TruffleBoundary
    public static Charset findEncodingStrict(TruffleFile file) throws IOException {
        // Using Latin-1 to read the header avoids exceptions on non-ascii characters
        try (BufferedReader reader = file.newBufferedReader(StandardCharsets.ISO_8859_1)) {
            return findEncodingStrict(reader);
        }
    }

    @TruffleBoundary
    public static Charset findEncodingStrict(String source) {
        try (BufferedReader reader = new BufferedReader(new StringReader(source))) {
            return findEncodingStrict(reader);
        } catch (IOException e) {
            // Shouldn't happen on a string
            throw new RuntimeException(e);
        }
    }

    @TruffleBoundary
    public static Charset findEncodingStrict(byte[] source, int sourceLen) {
        // Using Latin-1 to read the header avoids exceptions on non-ascii characters
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(new ByteArrayInputStream(source, 0, sourceLen), StandardCharsets.ISO_8859_1))) {
            return findEncodingStrict(reader);
        } catch (IOException e) {
            // Shouldn't happen on a string
            throw new RuntimeException(e);
        }
    }

    @Override
    public Charset findEncoding(TruffleFile file) throws IOException {
        try {
            return findEncodingStrict(file);
        } catch (InvalidEncodingException e) {
            // We cannot throw a SyntaxError at this point, but the parser will revalidate this.
            // Return Latin-1 so that it doesn't throw encoding errors before getting to the
            // parser, because Truffle would otherwise default to UTF-8
            return StandardCharsets.ISO_8859_1;
        }
    }
}

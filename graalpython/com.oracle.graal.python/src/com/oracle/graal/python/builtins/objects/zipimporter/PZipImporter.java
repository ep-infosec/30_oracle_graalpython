/*
 * Copyright (c) 2017, 2022, Oracle and/or its affiliates.
 * Copyright (c) 2013, Regents of the University of California
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.graal.python.builtins.objects.zipimporter;

import static com.oracle.graal.python.builtins.objects.str.StringUtils.cat;
import static com.oracle.graal.python.nodes.StringLiterals.T_DOT;
import static com.oracle.graal.python.nodes.StringLiterals.T_PY_EXTENSION;
import static com.oracle.graal.python.util.PythonUtils.TS_ENCODING;
import static com.oracle.graal.python.util.PythonUtils.toTruffleStringUncached;
import static com.oracle.graal.python.util.PythonUtils.tsLiteral;
import static com.oracle.graal.python.util.PythonUtils.tsbCapacity;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.NoSuchFileException;
import java.nio.file.StandardOpenOption;
import java.util.EnumSet;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.oracle.graal.python.builtins.objects.common.HashingStorage;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageGetIterator;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIterator;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorKey;
import com.oracle.graal.python.builtins.objects.common.HashingStorageNodes.HashingStorageIteratorNext;
import com.oracle.graal.python.builtins.objects.dict.PDict;
import com.oracle.graal.python.builtins.objects.object.PythonBuiltinObject;
import com.oracle.graal.python.builtins.objects.str.StringNodes.StringReplaceNode;
import com.oracle.graal.python.builtins.objects.tuple.PTuple;
import com.oracle.graal.python.runtime.PythonContext;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.object.Shape;
import com.oracle.truffle.api.strings.TruffleString;
import com.oracle.truffle.api.strings.TruffleStringBuilder;

public class PZipImporter extends PythonBuiltinObject {

    private static final TruffleString T_INIT_PY = tsLiteral("__init__.py");

    /**
     * pathname of the Zip archive
     */
    private TruffleString archive;

    /**
     * file prefix: "a/sub/directory/"
     */
    private TruffleString prefix;

    /**
     * dict with file info {path: toc_entry}
     */
    private PDict files;

    /**
     * Cache of the files in the zipfile. Exported in ZipimportModuleBuiltins
     */
    private final PDict moduleZipDirectoryCache;

    /**
     * Is the entry source or package
     */
    private static enum EntryType {
        IS_SOURCE,
        IS_PACKAGE
    }

    /**
     * The separatorChar used in the context for this importer
     */
    private final TruffleString separator;

    private static class SearchOrderEntry {

        TruffleString suffix;
        EnumSet<EntryType> type;

        SearchOrderEntry(TruffleString suffix, EnumSet<EntryType> type) {
            this.suffix = suffix;
            this.type = type;
        }
    }

    protected static class ModuleCodeData {

        TruffleString code;
        boolean isPackage;
        TruffleString path;

        ModuleCodeData(TruffleString code, boolean isPackage, TruffleString path) {
            this.code = code;
            this.isPackage = isPackage;
            this.path = path;
        }
    }

    /**
     * Defines how the source and module will be searched in archive.
     */
    private final SearchOrderEntry[] searchOrder;

    /**
     * Module information
     */
    public static enum ModuleInfo {
        ERROR,
        NOT_FOUND,
        MODULE,
        PACKAGE
    }

    public PZipImporter(Object cls, Shape instanceShape, PDict zipDirectoryCache, TruffleString separator) {
        super(cls, instanceShape);
        this.archive = null;
        this.prefix = null;
        this.separator = separator;
        this.moduleZipDirectoryCache = zipDirectoryCache;
        this.searchOrder = defineSearchOrder();
    }

    private SearchOrderEntry[] defineSearchOrder() {
        return new SearchOrderEntry[]{
                        new SearchOrderEntry(cat(separator, T_INIT_PY),
                                        enumSetOf(EntryType.IS_PACKAGE, EntryType.IS_SOURCE)),
                        new SearchOrderEntry(T_PY_EXTENSION, enumSetOf(EntryType.IS_SOURCE))
        };
    }

    @TruffleBoundary
    private static <E extends Enum<E>> EnumSet<E> enumSetOf(E e1) {
        return EnumSet.of(e1);
    }

    @TruffleBoundary
    private static <E extends Enum<E>> EnumSet<E> enumSetOf(E e1, E e2) {
        return EnumSet.of(e1, e2);
    }

    public PDict getZipDirectoryCache() {
        return moduleZipDirectoryCache;
    }

    /**
     *
     * @return pathname of the Zip archive
     */
    public TruffleString getArchive() {
        return archive;
    }

    /**
     *
     * @return file prefix: "a/sub/directory/"
     */
    public TruffleString getPrefix() {
        return prefix;
    }

    /**
     *
     * @return dict with file info {path: toc_entry}
     */
    public PDict getFiles() {
        return files;
    }

    public void setArchive(TruffleString archive) {
        this.archive = archive;
    }

    public void setPrefix(TruffleString prefix) {
        this.prefix = prefix;
    }

    public void setFiles(PDict files) {
        this.files = files;
    }

    protected TruffleString getSubname(TruffleString fullname) {
        int len = fullname.codePointLengthUncached(TS_ENCODING);
        int i = fullname.lastIndexOfCodePointUncached('.', len, 0, TS_ENCODING);
        if (i >= 0) {
            return fullname.substringUncached(i + 1, len - i - 1, TS_ENCODING, true);
        }
        return fullname;
    }

    @TruffleBoundary
    protected TruffleString makeFilename(TruffleString fullname) {
        return cat(prefix, StringReplaceNode.getUncached().execute(getSubname(fullname), T_DOT, separator, -1));
    }

    protected PTuple getEntry(TruffleString filenameAndSuffix) {
        return (PTuple) files.getItem(filenameAndSuffix);
    }

    @TruffleBoundary
    protected TruffleString makePackagePath(TruffleString fullname) {
        return cat(archive, separator, prefix, getSubname(fullname));
    }

    /**
     *
     * @param filenameAndSuffix
     * @return code
     * @throws IOException
     */
    @TruffleBoundary
    public static TruffleString getCodeFromArchive(PythonContext context, TruffleString filenameAndSuffix, TruffleString archive) throws IOException {
        InputStream fileStream = null;
        try {
            TruffleFile f = context.getEnv().getPublicTruffleFile(archive.toJavaStringUncached());

            fileStream = f.newInputStream(StandardOpenOption.READ);
            // This is not correct - it is wrong to read zip files by just skipping to the next
            // local file header, instead, zip files should always be read from the back, by
            // scanning for the EOCD record and possibly the EOCD Locator to find a Zip64 EOCD64.
            // Only the entries and offsets stored in those are valid entries of the Zip file. The
            // java.util.zip.ZipFile class handles all that, but it only works for java.io.File;
            // this would break out of our TruffleFile sandbox. Alternative libraries such as zip4j
            // also do not seem to work properly when just fed input streams. For now, we will just
            // skip to the first local file header and hope for the best...
            int offset = 0;
            byte[] data = fileStream.readNBytes(4096);
            outer: while (data.length >= 30) { // local file header is 30 bytes minimum
                int i = 0;
                for (; i < data.length - 30; i++) {
                    // zip headers are stored little endian, match the integer value of "PK\03\04",
                    // the local file header signature
                    if (data[i] == 'P' && data[i + 1] == 'K' && data[i + 2] == 0x03 && data[i + 3] == 0x04) {
                        fileStream.close();
                        fileStream = f.newInputStream(StandardOpenOption.READ);
                        fileStream.skip(offset + i);
                        break outer;
                    }
                }
                offset += 4096;
                data = fileStream.readNBytes(4096);
            }

            ZipInputStream zis = new ZipInputStream(fileStream);
            ZipEntry entry;
            String filenameAndSuffixStr = filenameAndSuffix.toJavaStringUncached();
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.getName().equals(filenameAndSuffixStr)) {
                    break;
                }
            }
            if (entry == null) {
                throw new NoSuchFileException(filenameAndSuffixStr);
            }

            // reading the file should be done better?
            BufferedReader reader = new BufferedReader(new InputStreamReader(zis));
            int size = (int) entry.getSize();
            if (size < 0) {
                size = (int) entry.getCompressedSize();
            }
            TruffleString lineSeparator = toTruffleStringUncached(System.lineSeparator());
            TruffleStringBuilder code = TruffleStringBuilder.create(TS_ENCODING, tsbCapacity(size < 16 ? 16 : size));
            String line;
            while ((line = reader.readLine()) != null) {
                code.appendStringUncached(toTruffleStringUncached(line));
                code.appendStringUncached(lineSeparator);
            }
            reader.close();
            return code.toStringUncached();
        } catch (IOException e) {
            throw e;
        } finally {
            if (fileStream != null) {
                try {
                    fileStream.close();
                } catch (IOException e) {
                    // just ignore it.
                }
            }
        }
    }

    /**
     * Return module information for the module with the fully qualified name.
     *
     * @param fullname the fully qualified name of the module
     * @return the module's information
     */
    @TruffleBoundary
    protected final ModuleInfo getModuleInfo(TruffleString fullname) {
        TruffleString path = makeFilename(fullname);

        for (SearchOrderEntry entry : searchOrder) {
            PTuple importEntry = getEntry(cat(path, entry.suffix));
            if (importEntry == null) {
                continue;
            }

            if (entry.type.contains(EntryType.IS_PACKAGE)) {
                return ModuleInfo.PACKAGE;
            }
            return ModuleInfo.MODULE;
        }
        return ModuleInfo.NOT_FOUND;
    }

    @TruffleBoundary
    protected boolean isDir(TruffleString path) {
        TruffleString dirPath = cat(path, separator);
        HashingStorage filesStorage = files.getDictStorage();
        HashingStorageIterator it = HashingStorageGetIterator.executeUncached(filesStorage);
        while (HashingStorageIteratorNext.executeUncached(filesStorage, it)) {
            Object key = HashingStorageIteratorKey.executeUncached(filesStorage, it);
            if (key instanceof TruffleString && ((TruffleString) key).equalsUncached(dirPath, TS_ENCODING)) {
                return true;
            }
        }
        return false;
    }

    @TruffleBoundary
    protected TruffleString getModulePath(TruffleString modPath) {
        return cat(this.archive, separator, modPath);
    }

    /**
     *
     * @param fullname
     * @return itself if the module is in this importer, otherwise null
     */
    protected final PZipImporter findModule(TruffleString fullname) {
        ModuleInfo moduleInfo = getModuleInfo(fullname);
        if (moduleInfo == ModuleInfo.ERROR || moduleInfo == ModuleInfo.NOT_FOUND) {
            return null;
        }
        return this;
    }

    @TruffleBoundary
    protected final ModuleCodeData getModuleCode(PythonContext context, TruffleString fullname) throws IOException {
        TruffleString path = makeFilename(fullname);
        TruffleString fullPath = makePackagePath(fullname);

        for (SearchOrderEntry entry : searchOrder) {
            TruffleString suffix = entry.suffix;
            TruffleString searchPath = cat(path, suffix);
            TruffleString fullSearchPath = cat(fullPath, suffix);

            PTuple tocEntry = getEntry(searchPath);
            if (tocEntry == null) {
                continue;
            }

            boolean isPackage = entry.type.contains(EntryType.IS_PACKAGE);

            TruffleString code;
            try {
                code = getCodeFromArchive(context, searchPath, archive);
            } catch (IOException e) {
                throw new IOException("Can not read code from " + makePackagePath(searchPath), e);
            }
            return new ModuleCodeData(code, isPackage, fullSearchPath);
        }
        return null;
    }

}

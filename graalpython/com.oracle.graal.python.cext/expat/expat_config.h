/* Copyright (c) 2019, 2021, Oracle and/or its affiliates.
 * Copyright (C) 1996-2020 Python Software Foundation
 *
 * Licensed under the PYTHON SOFTWARE FOUNDATION LICENSE VERSION 2
 */
/*
 * Expat configuration for python. This file is not part of the expat
 * distribution.
 */
#ifndef EXPAT_CONFIG_H
#define EXPAT_CONFIG_H

#include <pyconfig.h>
#ifdef WORDS_BIGENDIAN
#define BYTEORDER 4321
#else
#define BYTEORDER 1234
#endif

#define HAVE_MEMMOVE 1

#define XML_NS 1
#define XML_DTD 1
#define XML_CONTEXT_BYTES 1024

#endif /* EXPAT_CONFIG_H */

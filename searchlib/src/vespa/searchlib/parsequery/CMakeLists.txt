# Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
vespa_add_library(searchlib_parsequery OBJECT
    SOURCES
    parse.cpp
    simplequerystack.cpp
    stackdumpiterator.cpp
    DEPENDS
)

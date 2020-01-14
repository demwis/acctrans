package com.demwis.common

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.datatype.jdk8.Jdk8Module
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinModule

fun createObjectMapper(): ObjectMapper = ObjectMapper()
    .registerModule(KotlinModule())
    .registerModule(Jdk8Module())
    .registerModule(JavaTimeModule())
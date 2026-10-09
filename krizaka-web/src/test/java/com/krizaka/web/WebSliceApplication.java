package com.krizaka.web;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Import;

/**
 * The application {@code @WebMvcTest} slices start: no component scan, so only the auto-configured
 * kit beans and the test controller are present — exactly what a consuming service gets.
 */
@SpringBootConfiguration
@EnableAutoConfiguration
@Import(SampleController.class)
public class WebSliceApplication {}

package com.sequence.trace;

import java.util.Map;

import lombok.Data;

@Data
public class MethodData {
	private String apiContextURL;
	private Map<String, String> methodCallMap;

}

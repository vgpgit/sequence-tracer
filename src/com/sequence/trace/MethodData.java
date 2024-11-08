package com.sequence.trace;

import java.util.Map;

import lombok.Data;

@Data
public class MethodData {
	private String apiContextURL;
	private String sqlQuery;
	private Map<String, String> methodCallMap;

}

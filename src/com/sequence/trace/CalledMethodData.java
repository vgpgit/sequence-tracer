package com.sequence.trace;

import java.util.List;

import lombok.Data;

@Data
public class CalledMethodData {
	private String refVariable;
	private String calledMethodName;
	private List<String> argumentsList;
}

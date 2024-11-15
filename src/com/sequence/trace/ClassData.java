package com.sequence.trace;

import java.util.List;
import java.util.Map;

import lombok.Data;

@Data
public class ClassData {
	private String classKey;
	private String packageName;
	private List<String> importsList;
	private Map<String, FieldData> fieldsMap;
	private Map<String, MethodData> methodsMap;
	private String associatedEntity;
}

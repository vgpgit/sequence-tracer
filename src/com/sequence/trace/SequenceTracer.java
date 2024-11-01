package com.sequence.trace;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

public class SequenceTracer {

	private static Map<String, ClassData> classDataMap;
	private static Map<String, String> interfaceClassMap;
	private static StringBuilder fileContent;
	
	public static void main(String[] args) {
		
		try {
			Path startPath = Paths.get("/Users/vgp/Venu/CodeBase/GitHub_Repository/SpringBoot/SpringBatch/src/main/java");
			String basePackage = "com.example.springbatch";
			String sequenceStartPackage = "com.example.springbatch";
			
			fileContent = new StringBuilder();
			classDataMap = new LinkedHashMap<>();
			List<String> sequenceClassKeyList = new ArrayList<>();
			
			List<Path> pathList = Files.walk(startPath)
										.filter(path -> path.toFile().isFile())
										.filter(path -> path.toFile().getAbsolutePath().endsWith(".java"))
										.collect(Collectors.toList());
			
			System.out.println(pathList.size());
			
			interfaceClassMap = new LinkedHashMap<>();
			
			//********************************Data preparation - start**************************************
			
			for (Path path: pathList) {
				System.out.println("");
				System.out.println(path.toAbsolutePath());
				
				ClassData classData = new ClassData();
				CompilationUnit compilationUnit = StaticJavaParser.parse(path.toFile());
				ClassOrInterfaceDeclaration classOrInterfaceDeclaration = null;
				
				try {
					classOrInterfaceDeclaration = compilationUnit.findAll(ClassOrInterfaceDeclaration.class).get(0);
				} catch (Exception ex) {
					//ex.printStackTrace();
					System.out.println("-PARSE ERROR-");
					continue;
				}
				
				if (classOrInterfaceDeclaration.isInterface()) {
					System.out.println("-INTERFACE-");
				}
				else if (!classOrInterfaceDeclaration.getImplementedTypes().isEmpty()) {
					ClassOrInterfaceType classOrInterfaceType = classOrInterfaceDeclaration.getImplementedTypes().get(0); 
					interfaceClassMap.put(classOrInterfaceType.getNameAsString(), classOrInterfaceDeclaration.getFullyQualifiedName().get());
				}
				
				NodeList<ImportDeclaration> importDeclarationList = compilationUnit.getImports();
				List<FieldDeclaration> fieldDeclaration = classOrInterfaceDeclaration.getFields();
				List<MethodDeclaration> methodDeclaration = classOrInterfaceDeclaration.getMethods();
				
				//set package name
				classData.setPackageName(compilationUnit.getPackageDeclaration().get().getNameAsString());
				
				//set classKey
				classData.setClassKey(classOrInterfaceDeclaration.getFullyQualifiedName().get());
				
				//add imports to list
				List<String> importsList = new ArrayList<>();
				for (ImportDeclaration importDeclaration: importDeclarationList) {
					//System.out.println(importDeclaration.getNameAsString());
					if (importDeclaration.isAsterisk()) {
						importsList.add(importDeclaration.getNameAsString() + ".*");
					} else {
						importsList.add(importDeclaration.getNameAsString());
					}
				}
				classData.setImportsList(importsList);
				
				//set fields (member variables ) map
				Map<String, String> fieldsMap = new LinkedHashMap<>();
				for (FieldDeclaration field: fieldDeclaration) {
					for (VariableDeclarator variable: field.getVariables()) {
						fieldsMap.put(variable.getNameAsString(), variable.getTypeAsString());
					}
				}
				classData.setFieldsMap(fieldsMap);
				
				//set methods map
				Map<String, MethodData> methodsMap = new LinkedHashMap<>();
				for (MethodDeclaration method: methodDeclaration) {
					System.out.println(method.getNameAsString() + "--" + method.getParameters().size());
					
					MethodData methodData = new MethodData();
					Map<String, String> methodCallMap = new LinkedHashMap<>();
					
					//set apiContextURL if available
					if (!method.getAnnotations().isEmpty()) {
						methodData.setApiContextURL(getApiContextURL(method));
					}
					
					method.accept(new VoidVisitorAdapter<Void>() {
						@Override
						public void visit(MethodCallExpr n, Void arg) {
							//found the method call
							//System.out.println(n.getName().toString() + "----" + n.getArguments().size());
							String methodName = n.getName().toString() + "(" + n.getArguments().size() + ")";
							String refType = n.getScope().isPresent()? n.getScope().get().toString(): null;
							//System.out.println(refType + methodName);
							
							if (refType == null && n.getName().toString().equals(method.getNameAsString())) { //recursion case
								methodCallMap.put(refType + "." + methodName + "_recursion", refType);
							}
							else {
								methodCallMap.put(refType + "." + methodName, refType);
							}
							
							super.visit(n, arg);
						}
					}, null);
					
					methodData.setMethodCallMap(methodCallMap);
					methodsMap.put(method.getNameAsString() + "(" + method.getParameters().size() + ")", methodData);
				}//end of for loop
				classData.setMethodsMap(methodsMap);
				
				//set list of classKeys to be sequenced later
				if (classData.getClassKey().startsWith(sequenceStartPackage)) {
					sequenceClassKeyList.add(classData.getClassKey());
				}
				
				classDataMap.put(classData.getClassKey(), classData);
				
			}//for loop - pathlist
			
			System.out.println("interfaceClassMap----" + interfaceClassMap.toString());
			System.out.println(sequenceClassKeyList);
			System.out.println(System.lineSeparator() + System.lineSeparator() + System.lineSeparator());
			
			//********************************Data preparation - end**************************************
			
			//********************************Sequence tracing - start**************************************
			for (String classKey: sequenceClassKeyList) {
				ClassData classData = classDataMap.get(classKey);
				log("Class: " + classKey);
				log("API Methods: " + classData.getMethodsMap().keySet());
				log("");
				
				for (String methodName: classData.getMethodsMap().keySet()) {
					log("[" + methodName + "]\t[" + classData.getMethodsMap().get(methodName).getApiContextURL() + "]"); //controller api method
					
					findMethodCallHierarchyRecursively(classData, methodName, "\t", false);
					
					log(System.lineSeparator());
				}//for 2
				
				//break;
				log("-------------------------------------------------------------------------------");
			}//for 1
			
			//write to file
			writeFile(basePackage + ".txt", fileContent.toString());
			
			//********************************Sequence tracing - end**************************************
		}
		catch (Exception ex) {
			ex.printStackTrace();
		}

	}
	
	private static void log(String msg) {
		System.out.println(msg);
		fileContent.append(msg).append(System.lineSeparator());
	}
	
	private static void findMethodCallHierarchyRecursively(ClassData classData, String methodName, String tab, boolean isPrivate) {
		
		if (!isPrivate) {
			log(tab + "->" + classData.getClassKey() + "." + methodName);
		}
		
		MethodData methodData = classData.getMethodsMap().get(methodName); //this gets all called method details for current method
		//System.out.println(methodData.getMethodCallMap().toString());
		
		if (methodData == null) { //this happens when we call the method which is part of library like findById() or save() of crudrepository but not part of our codebase
			return;
		}
		
		for (String nextCalledMethod: methodData.getMethodCallMap().keySet()) { //iterate all called methods
			//System.out.println("nextCallMethod: " + nextCallMethod);
			String callReference = methodData.getMethodCallMap().get(nextCalledMethod); //get reference of called method
			
			if (callReference != null && classData.getFieldsMap().containsKey(callReference)) { // check if that reference is a class field (member variable)
				String refType = classData.getFieldsMap().get(callReference); //get the interface/class type of that field reference
				//System.out.println("refType: " + refType);
				
				ClassData nextClassInSequence = null;
				if (interfaceClassMap.containsKey(refType)) {
					nextClassInSequence = classDataMap.get(interfaceClassMap.get(refType));
				}
				
				String nextClassKey = null;
				if (nextClassInSequence == null) {
					nextClassKey = getFullyQualifiedType(classData.getPackageName(), classData.getImportsList(), refType);
					if (nextClassKey != null) {
						nextClassInSequence = classDataMap.get(nextClassKey);
					}
				}
				
				if (nextClassInSequence != null) { //when type and method part of codebase
					//call next method in seuqence only if classData exists for that method
					findMethodCallHierarchyRecursively(nextClassInSequence,
							nextCalledMethod.indexOf(".") > 0? nextCalledMethod.substring(nextCalledMethod.indexOf(".") + 1): nextCalledMethod, //strip reference and send only method name
							tab + "\t", 
							false);
				}
				else {
					//has reference field but type is part of library and not from codebase
					log(tab + "\t->" + nextClassKey + "." + nextCalledMethod.substring(nextCalledMethod.indexOf(".") + 1));
				}
			}
			else {
				//this is for private methods or method call without reference like static methods
				if (nextCalledMethod.startsWith("null.")) {
					log (tab + "\t->" + nextCalledMethod.substring(nextCalledMethod.indexOf(".") + 1));
					
					//find sequence of private method
					findMethodCallHierarchyRecursively(classData, 
							nextCalledMethod.substring(nextCalledMethod.indexOf(".") + 1), 
							tab + "\t",
							true);
				}
			}
		}//end of for loop
	}
	
	private static String getFullyQualifiedType(String packageName, List<String> importsList, String typeName) {
		String fullyQualifiedType = null;
		boolean found = false;
		
		for (String importName: importsList) { //type is available from imports list
			if (importName.endsWith(typeName)) {
				fullyQualifiedType = importName;
				found = true;
				break;
			}
		}
		
		if (!found) {
			for (String importName: importsList) { //type is part of imports *
				if (importName.endsWith(".*") && classDataMap.containsKey(importName.replace("*", typeName))) {
					fullyQualifiedType = importName.replace("*", typeName);
					found = true;
					break;
				}
			}
		}
		
		if (!found) {
			if (classDataMap.containsKey(packageName + "." + typeName)) { //type is part of current package itself
				fullyQualifiedType = packageName + "." + typeName;
			}
		}
		
		return fullyQualifiedType;
	}
	
	private static String getApiContextURL(MethodDeclaration methodDeclaration) {
		String apiContextURL = null;
		
		for (AnnotationExpr annotation: methodDeclaration.getAnnotations()) {
			String annotationStr = annotation.getName().asString();
			
			if (annotationStr.equals("PostMapping")
					|| annotationStr.equals("GetMapping")
					|| annotationStr.equals("PutMapping")
					|| annotationStr.equals("DeleteMapping")) {
				//get value of value attribute
				/*for (Node node: annotation.getChildNodes()) {
					if (node.toString().contains("value")) {
						apiContextURL = node.toString().substring(node.toString().indexOf("=") + 2);
						break;
					}
				}*/
				
				apiContextURL = annotation.toString();
				break;
			}
		}
		
		return apiContextURL;
	}

	public static void writeFile(String absFilePath, String content) throws Exception {
		try (BufferedWriter bw = new BufferedWriter(new FileWriter(new File(absFilePath)))) {
			bw.write(content);
		} catch (Exception ex) {
			throw ex;
		}
	}
}


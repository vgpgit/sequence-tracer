package com.sequence.trace;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.InputStream;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.stream.Collectors;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AnnotationExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.type.Type;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

public class SequenceTracer {

	private static Map<String, ClassData> classDataMap;
	private static Map<String, String> interfaceClassMap;
	private static StringBuilder fileContent;
	private static Map<String, String> jpaNamedQueryMap;
	
	public static void main(String[] args) {
		
		try {
			Properties properties = new Properties();
			try (InputStream is = new FileInputStream("src/resources/tracer.properties")) {
				properties.load(is);
			} catch (Exception ex) {
				System.out.println("Unable to find thee specified properties file");
				ex.printStackTrace();
				System.exit(1);
			}
			
			Path sourceStartPath = Paths.get(properties.getProperty("source.start.path"));
			String jpaNamedQueryPath = properties.getProperty("jpa.named.queries.path");
			String basePackage = properties.getProperty("base.package");
			String sequenceStartPackage = properties.getProperty("sequence.start.package");
			
			//load jpa-named-query file if exists
			jpaNamedQueryMap = new LinkedHashMap<>();
			if (jpaNamedQueryPath != null) {
				try (InputStream is = new FileInputStream(jpaNamedQueryPath)) {
					properties.load(is);
					jpaNamedQueryMap = properties.entrySet().stream()
											.collect(Collectors.toMap(e -> (String) e.getKey(), e -> (String) e.getValue()));
				} catch (Exception ex) {
					System.out.println("Unable to find thee specified properties file");
					ex.printStackTrace();
					System.exit(1);
				}
			}
			
			fileContent = new StringBuilder();
			classDataMap = new LinkedHashMap<>();
			List<String> sequenceClassKeyList = new ArrayList<>();
			
			List<Path> pathList = Files.walk(sourceStartPath)
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
				final ParserConfiguration parserConfiguration = new ParserConfiguration();
				parserConfiguration.setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17);
				JavaParser javaParser = new JavaParser(parserConfiguration);
				CompilationUnit compilationUnit = javaParser.parse(path.toFile()).getResult().get();
				ClassOrInterfaceDeclaration classOrInterfaceDeclaration;
				
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
				
				//assign associated entity name if its repository interface
				if (!classOrInterfaceDeclaration.getExtendedTypes().isEmpty()) {
					for (ClassOrInterfaceType classOrInterfaceType: classOrInterfaceDeclaration.getExtendedTypes()) {
						if (classOrInterfaceType.getTypeArguments().isPresent()) {
							for (Type type: classOrInterfaceType.getTypeArguments().get()) {
								if (type.asString().toUpperCase().contains("ENTITY")) {
									classData.setAssociatedEntity(type.asString());
									break;
								}
							}
							
							if (classData.getAssociatedEntity() == null) { //if entity class name not endswith Entity
								classData.setAssociatedEntity(classOrInterfaceType.getTypeArguments().get().get(0).asString());
							}
							
							break;
						}
					}
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
				Map<String, FieldData> fieldsMap = new LinkedHashMap<>();
				for (FieldDeclaration field: fieldDeclaration) {
					for (VariableDeclarator variable: field.getVariables()) {
						FieldData fieldData = new FieldData();
						fieldData.setFieldName(variable.getNameAsString());
						fieldData.setFieldType(variable.getTypeAsString());
						
						if (variable.getInitializer().isPresent()) {
							String value = variable.getInitializer().get().toString();
							value = value.replace("\"", "").replace("\\r\\n", "").replace(System.lineSeparator(), "");
							fieldData.setFieldValue(value);
						}
						
						fieldsMap.put(variable.getNameAsString(), fieldData); //reference variable -> fieldData map
					}
				}
				classData.setFieldsMap(fieldsMap);
				
				//set methods map
				Map<String, MethodData> methodsMap = new LinkedHashMap<>();
				for (MethodDeclaration method: methodDeclaration) {
					System.out.println(method.getNameAsString() + "--" + method.getParameters().size());
					
					MethodData methodData = new MethodData();
					Map<String, CalledMethodData> methodCallMap = new LinkedHashMap<>();
					
					//set values if annotations available
					if (!method.getAnnotations().isEmpty()) {
						methodData.setApiContextURL(getApiContextURL(method));
						methodData.setSqlQuery(getSQLQuery(classData, method));
					}
					
					method.accept(new VoidVisitorAdapter<Void>() {
						@Override
						public void visit(MethodCallExpr n, Void arg) {
							//found the method call
							CalledMethodData calledMethodData = new CalledMethodData();
							
							//System.out.println(n.getName().toString() + "----" + n.getArguments().size());
							String methodName = n.getName().toString() + "(" + n.getArguments().size() + ")";
							String refVariable = n.getScope().isPresent()? n.getScope().get().toString(): null;
							//System.out.println(refVariable + methodName);
							
							calledMethodData.setCalledMethodName(n.getName().toString());
							calledMethodData.setRefVariable(refVariable);
							
							//set arguments list if exists
							if (!n.getArguments().isEmpty()) {
								List<String> argumentsList = new ArrayList<>();
								for (Expression expression: n.getArguments()) {
									argumentsList.add(expression.toString());
								}
								calledMethodData.setArgumentsList(argumentsList);
							}
							
							if (refVariable == null && n.getName().toString().equals(method.getNameAsString())) { //recursion case
								methodCallMap.put(refVariable + "." + methodName + "_recursion", calledMethodData);
							}
							else {
								methodCallMap.put(refVariable + "." + methodName, calledMethodData);
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
		
		MethodData methodData = classData.getMethodsMap().get(methodName); //this gets all called method details for current method
		//System.out.println(methodData.getMethodCallMap().toString());
		
		if (!isPrivate) {
			String sqlQuery = null;
			if (methodData != null) {
				sqlQuery = classData.getMethodsMap().get(methodName).getSqlQuery();
			}
			
			log(tab + "->" + classData.getClassKey() + "." + methodName + (sqlQuery != null? "\t\t[" + sqlQuery + "]": ""));
		}
		
		if (methodData == null) { //this happens when we call the method which is part of library like findById() or save() of crudrepository but not part of our codebase
			return;
		}
		
		for (String nextCalledMethod: methodData.getMethodCallMap().keySet()) { //iterate all called methods
			//System.out.println("nextCallMethod: " + nextCallMethod);
			String callReference = methodData.getMethodCallMap().get(nextCalledMethod).getRefVariable(); //get reference of called method
			
			if (callReference != null && classData.getFieldsMap().containsKey(callReference)) { // check if that reference is a class field (member variable)
				String refType = classData.getFieldsMap().get(callReference).getFieldType(); //get the interface/class type of that field reference
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
					if (nextCalledMethod.toUpperCase().contains("JDBCTEMPLATE")) {//this block is to trace sql queries in all the ways
						extractSQLFromField(classData, methodData, nextCalledMethod, tab);
					}
					else {
						log(tab + "\t->" + nextClassKey + "." + nextCalledMethod.substring(nextCalledMethod.indexOf(".") + 1) + "\t\t[LIB]");
					}
				}
			}
			else {
				if (nextCalledMethod.startsWith("null.")) { //this is for private methods or method call without reference like static methods
					log (tab + "\t->" + nextCalledMethod.substring(nextCalledMethod.indexOf(".") + 1));
					
					//find sequence of private method
					findMethodCallHierarchyRecursively(classData, 
							nextCalledMethod.substring(nextCalledMethod.indexOf(".") + 1), 
							tab + "\t",
							true);
				}
				else if (nextCalledMethod.toUpperCase().contains("JDBCTEMPLATE")) {//this block is to trace sql queries in all the ways
					extractSQLFromField(classData, methodData, nextCalledMethod, tab);
				}
				else {
					//do nothing for all other local methods
				}
			}
		}//end of for loop
	}
	
	private static void extractSQLFromField(ClassData classData, MethodData methodData, String nextCalledMethod, String tab) {
		String firstArgument = "";
		if (methodData.getMethodCallMap().get(nextCalledMethod).getArgumentsList() != null) {
			firstArgument = methodData.getMethodCallMap().get(nextCalledMethod).getArgumentsList().get(0);
		}
		
		FieldData fieldData = classData.getFieldsMap().get(firstArgument);
		
		if (fieldData != null) { //if field match found on current class
			log(tab + "\t-> " + nextCalledMethod + " [" + firstArgument + " - " + fieldData.getFieldValue() + "]");
		}
		else if (firstArgument.contains(".")) {//look for field match on other class like constants class
			String refType = firstArgument.substring(0, firstArgument.indexOf("."));
			String field = firstArgument.substring(firstArgument.indexOf(".") + 1);
			String tempClassKey = getFullyQualifiedType(classData.getPackageName(), classData.getImportsList(), refType);
			ClassData tempClassData = classDataMap.get(tempClassKey);
			
			if (tempClassData != null) {
				log(tab + "\t-> " + nextCalledMethod + " [" + firstArgument + " - " + tempClassData.getFieldsMap().get(field).getFieldValue() + "]");
			} else {
				log(tab + "\t-> " + nextCalledMethod + " [" + firstArgument + "]");
			}
		}
		else {
			//TODO: Handle if query variable is statically imported as import statement
			
			//either query is hardcoded inline or from local variable
			log(tab + "\t-> " + nextCalledMethod + " [" + firstArgument + "]");
		}
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
	
	private static String getSQLQuery(ClassData classData, MethodDeclaration methodDeclaration) {
		String sqlQuery = null;
		
		for (AnnotationExpr annotation: methodDeclaration.getAnnotations()) {
			String annotationStr = annotation.getName().asString();
			
			if (annotationStr.equals("Query")
					|| annotationStr.equals("GetMapping")
					|| annotationStr.equals("PutMapping")
					|| annotationStr.equals("DeleteMapping")) {
				//get value of value attribute
				for (Node node: annotation.getChildNodes()) {
					if (node.toString().contains("value")) {
						sqlQuery = node.toString().substring(node.toString().indexOf("=") + 2);
						sqlQuery = sqlQuery.replace("\"", "")
											.replace("+", "")
											.replace("\\r\\n", "")
											.replace(System.lineSeparator(), "");
						break;
					}
				}
				
				if (sqlQuery == null) {//if no sql present in Query annotation then look at jpaNamedQueryMap
					sqlQuery = jpaNamedQueryMap.get(classData.getAssociatedEntity() + "." + methodDeclaration.getNameAsString());
				}
				
				break;
			}
		}
		
		return sqlQuery;
	}

	public static void writeFile(String absFilePath, String content) throws Exception {
		try (BufferedWriter bw = new BufferedWriter(new FileWriter(new File(absFilePath)))) {
			bw.write(content);
		} catch (Exception ex) {
			throw ex;
		}
	}
}


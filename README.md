# sequence-tracer

How to Run
----------
1) Set values for below properties in tracer.properties
	source.start.path=<absolute directory path>		[Mandatory]
	base.package=<fully qualified package name>		[Mandatory]
	sequence.start.package=<fully qualified start package name to trace> [Mandatory]
	jpa.named.queries.path=<absolute file path> [Optional - set only if exists]
	
2) Run main class: SequenceTracer.java

3) Report will be generated under project root in name of <base.package>.txt

Have fun!

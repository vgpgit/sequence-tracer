# sequence-tracer

Cool tiny tool to trace the method sequence on java files

How to Run
----------
1) Set values for below properties in tracer.properties <br>
	source.start.path=&lt;absolute directory path&gt;		[Mandatory] <br>
	base.package=&lt;fully qualified package name&gt;		[Mandatory] <br>
	sequence.start.package=&lt;fully qualified start package name to trace&gt; [Mandatory] <br>
	jpa.named.queries.path=&lt;absolute file path&gt; [Optional - set only if exists otherwise comment it] 
	
2) Run main class: SequenceTracer.java

3) Report will be generated under project root in name of <base.package>.txt

Have fun!

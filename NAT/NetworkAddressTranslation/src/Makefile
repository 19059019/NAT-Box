 JC = javac

.SUFFIXES: .java .class
.java.class:
	$(JC) $*.java

CLASSES = \
	networkaddresstranslation/TableEntry.java\
	networkaddresstranslation/Client.java\
	networkaddresstranslation/ClientInstance.java\
	networkaddresstranslation/NATBox.java

default: classes

classes: $(CLASSES:.java=.class)

clean:
	$(RM) networkaddresstranslation/*.class 
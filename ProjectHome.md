# kognitivAnt #
ant task that reads Eclipse, and MyEclipse, classpath file to define the path use to compile jar files.

## Description ##
It allows the user to specify relative mapping to projects and change container entries to real output folder. This way, you always keep in sync with the Eclipse classpath. Additionnaly, the task does support the ant fileset to provide additional file to the Path.


---

```
<taskdef name="eclipsePath" classname="ca.kognitivit.ant.MyAntClipse" />
<eclipsePath Verbose="false" PathId="java">
	<containerentry name="melibrary.com.genuitec.eclipse.j2eedt.core.MYECLIPSE_JAVAEE_5_CONTAINER" path="${librariesHome}\Genuitec\javaee5" />
	<relativepathmapping pattern="/Libraries" path="${librariesHome}" />
	<fileset dir="${distributionHome}">
		<include name="**/${distributionSubFolder}/AlfrescoAPI.jar" />
		<include name="**/Commons/*.jar" />
		<include name="**/UDDI/*.jar" />
	</fileset>
</eclipsePath>
```
package com.jvoegele.gradle.tasks.android;

import java.io.File;

import groovy.lang.MetaClass;
import groovy.util.XmlSlurper;

import org.gradle.api.internal.ConventionTask;
import org.gradle.api.tasks.TaskAction;

import com.jvoegele.gradle.plugins.android.AndroidPluginConvention;

/**
 * Uses the ProGuard tool to create a minimal JAR containing only those classes
 * and resources actually used by the application code.
 */
class ProGuard extends ConventionTask {
  private static final String PRO_GUARD_RESOURCE = "proguard/ant/task.properties"

  String artifactGroup = "net.sf.proguard"
  String artifactName = "proguard"
  String artifactVersion = "4.4"

  boolean warn = false
  boolean note = false
  boolean obfuscate = true
  
  public ProGuard () {
    // By default, this task is disabled - it has to be explicitly enabled by user in build.gradle
    enabled = false
  }
  
  public File getTempFile() {
    AndroidPluginConvention androidConvention = project.convention.plugins.android
    return new File (project.libsDir, androidConvention.getApkBaseName() + "-proguard-temp.jar")
  }

  public File getProguardConfig() {
    return new File(project.rootDir, "proguard.cfg")
  }

  @TaskAction
  protected void process() {

    defineProGuardTask()
    String tempFilePath = getTempFile().getAbsolutePath()

    Map proguardOptions = [
      'warn': warn,
      'obfuscate': obfuscate
    ]

    if (proguardConfig.exists()) {
      proguardOptions['configuration'] = proguardConfig
    } else {
      // use some minimal configuration if proguard.cfg doesn't exist
      // this is basically the same as what "android create project" generates
      proguardOptions['optimizationpasses'] = 5
      proguardOptions['usemixedcaseclassnames'] = false
      proguardOptions['skipnonpubliclibraryclasses'] = false
      proguardOptions['preverify'] = false
      proguardOptions['verbose'] = true
    }

    ant.proguard(proguardOptions) {
      injar(path: project.jar.archivePath)

      // Add each dependency into the ProGuard-processed JAR
      project.configurations.compile.files.each { dependency ->
        injar(file: dependency)
      }
      outjar(file: tempFilePath)
      libraryjar(file: ant['android.jar'])

      if (!proguardConfig.exists()) {
        // use some minimal configuration if proguard.cfg doesn't exist
        // this is basically the same as what "android create project" generates
        optimizations(filter: "!code/simplification/arithmetic,!field/*,!class/merging/*")

        keep(access: 'public', 'extends': 'android.app.Activity')
        keep(access: 'public', 'extends': 'android.app.Application')
        keep(access: 'public', 'extends': 'android.app.Service')
        keep(access: 'public', 'extends': 'android.content.BroadcastReceiver')
        keep(access: 'public', 'extends': 'android.content.ContentProvider')
        keep(access: 'public', 'extends': 'android.app.backup.BackupAgentHelper')
        keep(access: 'public', 'extends': 'android.preference.Preference')
        keep(access: 'public', name: 'com.android.vending.licensing.ILicensingService')
        keepclasseswithmembernames {
          method(access: 'native')
          constructor(access: 'public', parameters: 'android.content.Context,android.util.AttributeSet')
          constructor(access: 'public', parameters: 'android.content.Context,android.util.AttributeSet,int')
        }
        keepclassmembers('extends': 'java.lang.Enum') {
          method(access: 'public static', type: '**[]', name: 'values', parameters: '')
          method(access: 'public static', type: '**', name: 'valueOf', parameters: 'java.lang.String')
        }
        keep('implements': 'android.os.Parcelable') {
          field(access: 'public static final', type: 'android.os.Parcelable$Creator')
        }
      }

      keep(access: 'public', 'name': '**.R')
      keep('name': '**.R$*')
    }
                 
    // Update the output file of this task
    ant.move(file: tempFilePath, toFile: project.jar.archivePath, overwrite: true)
  }

  private boolean proGuardTaskDefined = false
  private void defineProGuardTask() {
    if (!proGuardTaskDefined) {
      project.configurations {
        proguard
      }
      project.dependencies {
        proguard group: artifactGroup, name: artifactName, version: artifactVersion
      }
      ant.taskdef(resource: PRO_GUARD_RESOURCE, classpath: project.configurations.proguard.asPath)
      proGuardTaskDefined = true
    }
  }
}

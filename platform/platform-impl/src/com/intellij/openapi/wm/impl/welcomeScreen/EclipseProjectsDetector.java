// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.welcomeScreen;

import com.intellij.ide.ProjectGroup;
import com.intellij.ide.RecentProjectsManager;
import com.intellij.ide.RecentProjectsManagerBase;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.containers.ContainerUtil;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.Callable;

class EclipseProjectsDetector {
  private final static Logger LOG = Logger.getInstance(EclipseProjectsDetector.class);

  public static void detectProjects(Runnable callback) {
    Callable<List<String>> collector;
    if (SystemInfo.isMac) {
      collector = new MacEclipseProjectsCollector();
    }
    else {
      return;
    }
    ApplicationManager.getApplication().executeOnPooledThread(() -> {
      try {
        List<String> projects = collector.call();
        if (projects.isEmpty()) return;
        RecentProjectsManagerBase manager = (RecentProjectsManagerBase)RecentProjectsManager.getInstance();
        ProjectGroup group = new ProjectGroup("Eclipse Projects");
        group.setProjects(projects);
        manager.addGroup(group);
        ApplicationManager.getApplication().invokeLater(callback);
      }
      catch (Exception e) {
        LOG.error(e);
      }
    });
  }
}

class MacEclipseProjectsCollector implements Callable<List<String>> {
  @Override
  public List<String> call() throws IOException {
    String path = "/Applications/Eclipse.app/Contents/Eclipse/configuration/.settings/org.eclipse.ui.ide.prefs";
    List<String> projects = new ArrayList<>();
    collectProjects(projects, Path.of(path));
    collectProjects(projects, Path.of(System.getProperty("user.home"), path));
    return projects;
  }

  static void collectProjects(List<String> projects, Path path) throws IOException {
    File file = path.toFile();
    if (!file.exists()) return;
    String prefs = FileUtil.loadFile(file);
    String[] workspaces = getWorkspaces(prefs);
    for (String workspace : workspaces) {
      projects.addAll(scanForProjects(workspace));
    }
  }

  static String[] getWorkspaces(String prefs) throws IOException {
    Properties properties = new Properties();
    properties.load(new StringReader(prefs));
    String workspaces = properties.getProperty("RECENT_WORKSPACES");
    return workspaces == null ? ArrayUtil.EMPTY_STRING_ARRAY : workspaces.split("\\n");
  }

  static List<String> scanForProjects(String workspace) {
    List<String> projects = new ArrayList<>();
    File[] files = new File(workspace).listFiles();
    if (files == null) {
      return projects;
    }
    for (File file : files) {
      String[] list = file.list();
      if (list != null && ContainerUtil.or(list, s -> ".project".equals(s)) && ContainerUtil.or(list, s -> ".classpath".equals(s))) {
        projects.add(file.getPath());
      }
    }
    return projects;
  }
}

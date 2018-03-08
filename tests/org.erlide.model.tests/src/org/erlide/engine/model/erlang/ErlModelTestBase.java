package org.erlide.engine.model.erlang;

import org.eclipse.core.runtime.CoreException;
import org.erlide.engine.model.root.IErlModule;
import org.erlide.engine.model.root.IErlProject;
import org.erlide.engine.util.ErlideTestUtils;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;

public class ErlModelTestBase {

    protected static IErlProject[] projects;

    protected static void setupProjects() throws CoreException {
        ErlideTestUtils.initProjects();
        // We set up projects here, it's quite costly
        final String name1 = "testproject1";
        final IErlProject project1 = ErlideTestUtils.createErlProject(name1);
        final String name2 = "testproject2";
        final IErlProject project2 = ErlideTestUtils.createErlProject(name2);
        ErlModelTestBase.projects = new IErlProject[] { project1, project2 };
    }

    @BeforeClass
    public static void setUpBeforeClass() throws Exception {
        ErlModelTestBase.setupProjects();
    }

    @AfterClass
    public static void tearDownAfterClass() throws Exception {
        ErlideTestUtils.deleteProjects();
    }

    protected IErlModule module;
    protected IErlProject project;

    public void setupModules() throws CoreException {
        ErlideTestUtils.initModulesAndIncludes();
        project = ErlModelTestBase.projects[0];
        module = ErlideTestUtils.createModule(ErlModelTestBase.projects[0], "xx.erl",
                "-module(xx).\n-include(\"yy.hrl\").\n"
                        + "f(A) ->\n    lists:reverse(A).\n");
    }

    protected void tearDownModules() throws CoreException {
        ErlideTestUtils.deleteModules();
        ErlideTestUtils.refreshProjects();
    }

    @Before
    public void setUp() throws Exception {
        setupModules();
    }

    @After
    public void tearDown() throws Exception {
        tearDownModules();
    }

}

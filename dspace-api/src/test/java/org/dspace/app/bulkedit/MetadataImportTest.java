/**
 * The contents of this file are subject to the license and copyright
 * detailed in the LICENSE and NOTICE files at the root of the source
 * tree and available online at
 *
 * http://www.dspace.org/license/
 */
package org.dspace.app.bulkedit;

import static junit.framework.TestCase.assertEquals;
import static junit.framework.TestCase.assertTrue;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.sql.SQLException;
import java.util.List;

import org.apache.commons.cli.ParseException;
import org.apache.commons.collections4.IteratorUtils;
import org.apache.commons.lang3.StringUtils;
import org.dspace.AbstractIntegrationTestWithDatabase;
import org.dspace.app.launcher.ScriptLauncher;
import org.dspace.app.scripts.handler.impl.TestDSpaceRunnableHandler;
import org.dspace.builder.CollectionBuilder;
import org.dspace.builder.CommunityBuilder;
import org.dspace.content.Collection;
import org.dspace.content.Community;
import org.dspace.content.Item;
import org.dspace.content.factory.ContentServiceFactory;
import org.dspace.content.service.ItemService;
import org.dspace.eperson.factory.EPersonServiceFactory;
import org.dspace.eperson.service.EPersonService;
import org.dspace.scripts.DSpaceRunnable;
import org.dspace.scripts.configuration.ScriptConfiguration;
import org.dspace.scripts.factory.ScriptServiceFactory;
import org.dspace.scripts.service.ScriptService;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class MetadataImportTest extends AbstractIntegrationTestWithDatabase {

    private final ItemService itemService
        = ContentServiceFactory.getInstance().getItemService();
    private final EPersonService ePersonService = EPersonServiceFactory.getInstance().getEPersonService();
    private Collection collection;

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        context.turnOffAuthorisationSystem();
        Community community = CommunityBuilder.createCommunity(context).build();
        this.collection = CollectionBuilder.createCollection(context, community).build();
        context.restoreAuthSystemState();
    }

    @Test
    public void metadataImportTest() throws Exception {
        String[] csv = {"id,collection,dc.title,dc.contributor.author",
            "+," + collection.getHandle() + ",\"Test Import 1\"," + "\"Donald, SmithImported\""};
        performImportScript(csv);
        Item importedItem = findItemByName("Test Import 1");

        assertTrue(
            StringUtils.equals(
                itemService.getMetadata(importedItem, "dc", "contributor", "author", Item.ANY).get(0).getValue(),
                "Donald, SmithImported"));
        eperson = ePersonService.findByEmail(context, eperson.getEmail());
        assertEquals(importedItem.getSubmitter(), eperson);

        context.turnOffAuthorisationSystem();
        itemService.delete(context, itemService.find(context, importedItem.getID()));
        context.restoreAuthSystemState();
    }

    @Test
    public void personMetadataImportTest() throws Exception {

        String[] csv = {"id,collection,dc.title,person.birthDate",
            "+," + collection.getHandle() + ",\"Test Import 2\"," + "2000"};
        performImportScript(csv);
        Item importedItem = findItemByName("Test Import 2");
        assertTrue(
            StringUtils.equals(
                itemService.getMetadata(importedItem, "person", "birthDate", null, Item.ANY)
                           .get(0).getValue(), "2000"));
        context.turnOffAuthorisationSystem();
        itemService.delete(context, itemService.find(context, importedItem.getID()));
        context.restoreAuthSystemState();
    }

    @Test(expected = ParseException.class)
    public void metadataImportWithoutEPersonParameterTest()
        throws IllegalAccessException, InstantiationException, ParseException {
        String fileLocation = new File(testProps.get("test.importcsv").toString()).getAbsolutePath();
        String[] args = new String[] {"metadata-import", "-f", fileLocation, "-s"};
        TestDSpaceRunnableHandler testDSpaceRunnableHandler = new TestDSpaceRunnableHandler();

        ScriptService scriptService = ScriptServiceFactory.getInstance().getScriptService();
        ScriptConfiguration scriptConfiguration = scriptService.getScriptConfiguration(args[0]);

        DSpaceRunnable script = null;
        if (scriptConfiguration != null) {
            script = scriptService.createDSpaceRunnableForScriptConfiguration(scriptConfiguration);
        }
        if (script != null) {
            script.initialize(args, testDSpaceRunnableHandler, null);
            script.run();
        }
    }


    private Item findItemByName(String name) throws SQLException {
        Item importedItem = null;
        List<Item> allItems = IteratorUtils.toList(itemService.findAll(context));
        for (Item item : allItems) {
            if (item.getName().equals(name)) {
                importedItem = item;
            }
        }
        return importedItem;
    }

    /**
     * Import mocked CSVs to test item creation behavior, deleting temporary file afterward.
     */
    public void performImportScript(String[] csv) throws Exception {
        File csvFile = File.createTempFile("dspace-test-import", "csv");
        BufferedWriter out = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(csvFile), "UTF-8"));
        for (String csvLine : csv) {
            out.write(csvLine + "\n");
        }
        out.flush();
        out.close();
        String fileLocation = csvFile.getAbsolutePath();
        try {
            String[] args = new String[] {"metadata-import", "-f", fileLocation, "-e", eperson.getEmail(), "-s"};
            TestDSpaceRunnableHandler testDSpaceRunnableHandler = new TestDSpaceRunnableHandler();
            ScriptLauncher
                .handleScript(args, ScriptLauncher.getConfig(kernelImpl), testDSpaceRunnableHandler, kernelImpl);
        } finally {
            csvFile.delete();
        }
    }

}

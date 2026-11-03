import java.io.File;
import java.util.NoSuchElementException;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.tree.TreePath;
import org.apache.jmeter.gui.GuiPackage;
import org.apache.jmeter.gui.tree.JMeterTreeListener;
import org.apache.jmeter.gui.tree.JMeterTreeModel;
import org.apache.jmeter.gui.tree.JMeterTreeNode;
import org.apache.jmeter.protocol.http.config.gui.HttpDefaultsGui;
import org.apache.jmeter.protocol.http.sampler.HTTPSamplerProxy;
import org.apache.jmeter.save.SaveService;
import org.apache.jmeter.testelement.TestElement;
import org.apache.jmeter.util.JMeterUtils;
import org.apache.jorphan.collections.HashTree;

/** Loads plans through JMeter's Swing configuration code without running samplers. */
public class VerifyJMeterGui {
    private static boolean checkedOriginalFailure;

    private static int inspect(HashTree tree, AtomicInteger defaults, AtomicInteger requests) {
        int count = 0;
        for (Object key : tree.list()) {
            TestElement element = (TestElement) key;
            count++;
            if (element instanceof HTTPSamplerProxy) {
                requests.incrementAndGet();
            }
            if (element.getPropertyAsString(TestElement.GUI_CLASS).endsWith("HttpDefaultsGui")) {
                defaults.incrementAndGet();
                if (!checkedOriginalFailure) {
                    TestElement broken = (TestElement) element.clone();
                    broken.removeProperty("HTTPsampler.Arguments");
                    try {
                        new HttpDefaultsGui().configure(broken);
                        throw new AssertionError("Missing-arguments regression fixture did not fail");
                    } catch (NoSuchElementException expected) {
                        if (!expected.getMessage().contains("HTTPsampler.Arguments")) {
                            throw expected;
                        }
                        System.out.println("PASS: original missing-arguments exception reproduced");
                        checkedOriginalFailure = true;
                    }
                }
            }
            count += inspect(tree.getTree(key), defaults, requests);
        }
        return count;
    }

    private static void checkPlan(File file) throws Exception {
        HashTree loaded = SaveService.loadTree(file);
        AtomicInteger defaults = new AtomicInteger();
        AtomicInteger requests = new AtomicInteger();
        int count = inspect(loaded, defaults, requests);
        if (defaults.get() != 1 || requests.get() == 0) {
            throw new AssertionError("Incomplete test plan: " + file);
        }

        JMeterTreeModel model = new JMeterTreeModel();
        JMeterTreeListener listener = new JMeterTreeListener(model);
        JTree swingTree = new JTree(model);
        listener.setJTree(swingTree);
        JMeterTreeNode root = (JMeterTreeNode) model.getRoot();
        JMeterTreeNode plan = (JMeterTreeNode) root.getChildAt(0);
        swingTree.setSelectionPath(new TreePath(plan.getPath()));
        GuiPackage.initInstance(listener, model);

        // The same addSubTree -> addComponent -> GUI.configure path used by File > Open.
        // No JMeter engine is created, so no login, HTTP call or stock change occurs.
        model.addSubTree(loaded, plan);
        System.out.println("PASS: " + file.getName() + " (" + count
                + " elements; " + requests.get() + " HTTP samplers)");
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            throw new IllegalArgumentException("Usage: VerifyJMeterGui <jmeter-home> <plan.jmx>...");
        }
        JMeterUtils.setJMeterHome(new File(args[0]).getAbsolutePath());
        JMeterUtils.loadJMeterProperties(new File(args[0], "bin/jmeter.properties").getPath());
        JMeterUtils.initLocale();
        JMeterUtils.setProperty("jmeter.known_look_and_feels", "");
        try {
            SwingUtilities.invokeAndWait(() -> {
                for (int i = 1; i < args.length; i++) {
                    try {
                        checkPlan(new File(args[i]));
                    } catch (Exception failure) {
                        throw new RuntimeException("GUI load failed: " + args[i], failure);
                    }
                }
            });
            System.out.println("PASS: all plans loaded; no samplers executed");
            System.exit(0);
        } catch (Throwable failure) {
            failure.printStackTrace();
            System.exit(1);
        }
    }
}

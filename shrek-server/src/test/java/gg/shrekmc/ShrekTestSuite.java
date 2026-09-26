package gg.shrekmc;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.ExcludeClassNamePatterns;
import org.junit.platform.suite.api.IncludeTags;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

@Suite(failIfNoTests = false)
@SuiteDisplayName("Shrek standalone tests")
@IncludeTags("Shrek")
@SelectPackages({"gg.shrekmc", "me.samsuik.sakura"})
@ExcludeClassNamePatterns(".*TestSuite")
@ConfigurationParameter(key = "TestSuite", value = "Shrek")
public class ShrekTestSuite {
}

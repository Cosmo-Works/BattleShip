package dev.cosmoworks.battleship;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.ExcludeClassNamePatterns;
import org.junit.platform.suite.api.IncludeTags;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

@Suite(failIfNoTests = false)
@SuiteDisplayName("Battleship tests that need registry values present, with all feature flags set")
@IncludeTags("AllFeatures")
@SelectPackages({"dev.cosmoworks.battleship", "me.samsuik.sakura"})
@ExcludeClassNamePatterns(".*TestSuite")
@ConfigurationParameter(key = "TestSuite", value = "AllFeatures")
public class BattleshipAllFeaturesTestSuite {
}

package dev.cosmoworks.battleship;

import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.ExcludeClassNamePatterns;
import org.junit.platform.suite.api.IncludeTags;
import org.junit.platform.suite.api.SelectPackages;
import org.junit.platform.suite.api.Suite;
import org.junit.platform.suite.api.SuiteDisplayName;

@Suite(failIfNoTests = false)
@SuiteDisplayName("Battleship standalone tests")
@IncludeTags("Battleship")
@SelectPackages({"dev.cosmoworks.battleship", "me.samsuik.sakura"})
@ExcludeClassNamePatterns(".*TestSuite")
@ConfigurationParameter(key = "TestSuite", value = "Battleship")
public class BattleshipTestSuite {
}

package thredds.server.config;
import static org.junit.Assert.fail;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import thredds.mock.web.MockTdsContextLoader;
import thredds.mock.web.TdsContentRootPath;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations={"/WEB-INF/applicationContext-tdsConfig.xml"},loader=MockTdsContextLoader.class)
@TdsContentRootPath(path = "/share/testcatalogs/content")
public class CdmInitTest {
	
	@Autowired
	private TdsContext tdsContext;
	
	@Autowired
	private CdmInit cdmInit;
	
	private MockServletContext servletContext;

	@Before
	public void setUp() throws Exception {
		//ServletContext to init the TdsContext
		servletContext =  new MockServletContext();
		tdsContext.init(servletContext);
	}	
	
	@Test
	public void testInit() {
		
		cdmInit.init(tdsContext);
		
		fail("Not yet implemented");
	}

}

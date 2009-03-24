/*
 * Copyright 1998-2009 University Corporation for Atmospheric Research/Unidata
 *
 * Portions of this software were developed by the Unidata Program at the
 * University Corporation for Atmospheric Research.
 *
 * Access and use of this software shall impose the following obligations
 * and understandings on the user. The user is granted the right, without
 * any fee or cost, to use, copy, modify, alter, enhance and distribute
 * this software, and any derivative works thereof, and its supporting
 * documentation for any purpose whatsoever, provided that this entire
 * notice appears in all copies of the software, derivative works and
 * supporting documentation.  Further, UCAR requests that the user credit
 * UCAR/Unidata in any publications that result from the use of this
 * software or in any product that includes this software. The names UCAR
 * and/or Unidata, however, may not be used in any advertising or publicity
 * to endorse or promote any products or commercial entity unless specific
 * written permission is obtained from UCAR/Unidata. The user also
 * understands that UCAR/Unidata is not obligated to provide the user with
 * any support, consulting, training or assistance of any kind with regard
 * to the use, operation and performance of this software nor to provide
 * the user with any updates, revisions, new versions or "bug fixes."
 *
 * THIS SOFTWARE IS PROVIDED BY UCAR/UNIDATA "AS IS" AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL UCAR/UNIDATA BE LIABLE FOR ANY SPECIAL,
 * INDIRECT OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING
 * FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT,
 * NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION
 * WITH THE ACCESS, USE OR PERFORMANCE OF THIS SOFTWARE.
 */

package thredds.server.config;

import thredds.servlet.ThreddsConfig;
import thredds.servlet.ServletUtil;
import thredds.servlet.FmrcInventoryServlet;
import ucar.nc2.dataset.NetcdfDataset;
import ucar.nc2.util.cache.FileCacheRaf;
import ucar.nc2.util.DiskCache2;
import ucar.nc2.util.DiskCache;
import ucar.nc2.ncml.Aggregation;
import ucar.nc2.ncml.AggregationFmrc;

import java.util.Calendar;
import java.util.Timer;
import java.util.TimerTask;
import java.io.File;

/**
 * A Singleton class to initialize the CDM, instantiated by Spring.
 * @author caron
 * @since Feb 20, 2009
 */
public class CdmInit {
  private DiskCache2 aggCache;
  private Timer timer;
  String fmrcDefinitionDirectory;

  public void setFmrcDefinitionDirectory(String dir) {
    fmrcDefinitionDirectory = dir;
  }


  void init(TdsContext tdsContext) {
    AggregationFmrc.setDefinitionDirectory(new File(tdsContext.getRootDirectory(), fmrcDefinitionDirectory));
    FmrcInventoryServlet.setDefinitionDirectory(new File(tdsContext.getRootDirectory(), fmrcDefinitionDirectory));

    // NetcdfFileCache : default is allow 200 - 400 open files, cleanup every 10 minutes
    int min = ThreddsConfig.getInt("NetcdfFileCache.minFiles", 200);
    int max = ThreddsConfig.getInt("NetcdfFileCache.maxFiles", 400);
    int secs = ThreddsConfig.getSeconds("NetcdfFileCache.scour", 10 * 60);
    if (max > 0) {
      NetcdfDataset.initNetcdfFileCache(min, max, secs);
    }

    // HTTP file access : // allow 20 - 40 open datasets, cleanup every 10 minutes
    min = ThreddsConfig.getInt("HTTPFileCache.minFiles", 25);
    max = ThreddsConfig.getInt("HTTPFileCache.maxFiles", 40);
    secs = ThreddsConfig.getSeconds("HTTPFileCache.scour", 10 * 60);
    if (max > 0) {
      ServletUtil.setFileCache( new FileCacheRaf(min, max, secs));
    }

    // turn off Grib extend indexing; indexes are automatically done every 10 minutes externally
    boolean extendIndex = ThreddsConfig.getBoolean("GribIndexing.setExtendIndex", false);
    ucar.nc2.iosp.grib.GribServiceProvider.setExtendIndex( extendIndex);

    boolean alwaysUseCache = ThreddsConfig.getBoolean("GribIndexing.alwaysUseCache", false);
    ucar.nc2.iosp.grib.GribServiceProvider.setIndexAlwaysInCache( alwaysUseCache);

    // optimization: netcdf-3 files can only grow, not have metadata changes
    ucar.nc2.NetcdfFile.setProperty("syncExtendOnly", "true");

    // persist joinNew aggregations. default every 24 hours, delete stuff older than 30 days
    String dir = ThreddsConfig.get("AggregationCache.dir", new File( tdsContext.getContentDirectory().getPath(), "cacheAged").getPath());
    int scourSecs = ThreddsConfig.getSeconds("AggregationCache.scour", 24 * 60 * 60);
    int maxAgeSecs = ThreddsConfig.getSeconds("AggregationCache.maxAge", 30 * 24 * 60 * 60);
    aggCache = new DiskCache2(dir, false, maxAgeSecs / 60, scourSecs / 60);
    Aggregation.setPersistenceCache(aggCache);

    // how to choose the typical dataset ?
    String typicalDataset = ThreddsConfig.get("Aggregation.typicalDataset", null);
    if (null != typicalDataset)
      Aggregation.setTypicalDatasetMode(typicalDataset);

    // Nj22 disk cache
    dir = ThreddsConfig.get("DiskCache.dir", new File( tdsContext.getContentDirectory(), "cache" ).getPath());
    boolean alwaysUse = ThreddsConfig.getBoolean("DiskCache.alwaysUse", false);
    scourSecs = ThreddsConfig.getSeconds("DiskCache.scour", 60 * 60);
    long maxSize = ThreddsConfig.getBytes("DiskCache.maxSize", (long) 1000 * 1000 * 1000);
    DiskCache.setRootDirectory(dir);
    DiskCache.setCachePolicy(alwaysUse);

    Calendar c = Calendar.getInstance(); // contains current startup time
    c.add(Calendar.SECOND, scourSecs / 2); // starting in half the scour time
    timer = new Timer();
    timer.scheduleAtFixedRate(new CacheScourTask(maxSize), c.getTime(), (long) 1000 * scourSecs);
  }

  public void destroy() throws Exception {
    if (timer != null) timer.cancel();
    NetcdfDataset.shutdown();
    if (aggCache != null) aggCache.exit();
  }

  private class CacheScourTask extends TimerTask {
    long maxBytes;

    CacheScourTask(long maxBytes) {
      this.maxBytes = maxBytes;
    }

    public void run() {
      StringBuilder sbuff = new StringBuilder();
      DiskCache.cleanCache(maxBytes, sbuff); // 1 Gbyte
      sbuff.append("----------------------\n");
      // cacheLog.info(sbuff.toString());
    }
  }

}
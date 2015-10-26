/*
Copyright (c) Microsoft Open Technologies, Inc.
All Rights Reserved
Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with the
License. You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0

THIS CODE IS PROVIDED ON AN *AS IS* BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, EITHER EXPRESS OR IMPLIED,
INCLUDING WITHOUT LIMITATION ANY IMPLIED WARRANTIES OR CONDITIONS OF TITLE, FITNESS FOR A PARTICULAR PURPOSE,
MERCHANTABLITY OR NON-INFRINGEMENT.

See the Apache 2 License for the specific language governing permissions and limitations under the License.
*/

package com.msopentech.thali.android.toronionproxy;

import static android.content.Context.MODE_PRIVATE;

import android.annotation.SuppressLint;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

import com.msopentech.thali.toronionproxy.FileUtilities;
import com.msopentech.thali.toronionproxy.OnionProxyContext;
import com.msopentech.thali.toronionproxy.WriteObserver;

import android.content.Context;

public class AndroidOnionProxyContext extends OnionProxyContext {
  private final Context context;

  private enum Arch {
    ARM64,
    MIPS64,
    AMD64,
    ARM,
    MIPS,
    X86
  };

  public AndroidOnionProxyContext(Context context, String workingSubDirectoryName) {
    super(context.getDir(workingSubDirectoryName, MODE_PRIVATE));
    this.context = context;
  }

  @Override
  public WriteObserver generateWriteObserver(File file) {
    return new AndroidWriteObserver(file);
  }

  @Override
  protected InputStream getAssetOrResourceByName(String fileName) throws IOException {
    try {
      return context.getResources().getAssets().open(fileName);
    } catch (IOException e) {
      return getClass().getResourceAsStream("/" + fileName);
    }
  }

  @Override
  public String getProcessId() {
    return String.valueOf(android.os.Process.myPid());
  }

  @Override
  public void installFiles() throws IOException, InterruptedException {
    super.installFiles();
    FileUtilities.cleanInstallOneFile(getAssetOrResourceByName(getPathToTorExecutable() + getTorExecutableFileName()),
        torExecutableFile);
  }

  @Override
  public String getPathToTorExecutable() {
    return "";

  }

  @SuppressLint("DefaultLocale")
  @Override
  protected String getTorExecutableFileName() {
    String arch = System.getProperty("os.arch").toLowerCase();
    System.out.println(arch);
    String exec = "tor.";
    if (arch.contains("64")) {
      if (arch.contains("arm"))
        return exec + Arch.ARM64.name().toLowerCase();
      else if (arch.contains("mips"))
        return exec + Arch.MIPS64.name().toLowerCase();
      else if (arch.contains("86") || arch.contains("amd"))
        return exec + Arch.AMD64.name().toLowerCase();
    } else {
      if (arch.contains("arm"))
        return exec + Arch.ARM.name().toLowerCase();
      else if (arch.contains("mips"))
        return exec + Arch.MIPS.name().toLowerCase();
      else if (arch.contains("86"))
        return exec + Arch.X86.name().toLowerCase();
    }
    throw new RuntimeException("We don't support Tor on this OS");
  }
}

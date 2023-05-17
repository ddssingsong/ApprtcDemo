
// Copyright 2023 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

// This file is autogenerated by
//     java_cpp_enum.py
// From
//     ../../rtc_base/network_monitor.h

package org.webrtc;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

@IntDef({
    NetworkPreference.NEUTRAL, NetworkPreference.NOT_PREFERRED
})
@Retention(RetentionPolicy.SOURCE)
public @interface NetworkPreference {
  int NEUTRAL = 0;
  int NOT_PREFERRED = -1;
}

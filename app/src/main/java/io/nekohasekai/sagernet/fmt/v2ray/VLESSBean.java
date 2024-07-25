/******************************************************************************
 *                                                                            *
 * Copyright (C) 2021 by nekohasekai <contact-sagernet@sekai.icu>             *
 *                                                                            *
 * This program is free software: you can redistribute it and/or modify       *
 * it under the terms of the GNU General Public License as published by       *
 * the Free Software Foundation, either version 3 of the License, or          *
 *  (at your option) any later version.                                       *
 *                                                                            *
 * This program is distributed in the hope that it will be useful,            *
 * but WITHOUT ANY WARRANTY; without even the implied warranty of             *
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the              *
 * GNU General Public License for more details.                               *
 *                                                                            *
 * You should have received a copy of the GNU General Public License          *
 * along with this program. If not, see <http://www.gnu.org/licenses/>.       *
 *                                                                            *
 ******************************************************************************/

package io.nekohasekai.sagernet.fmt.v2ray;

import androidx.annotation.NonNull;

import org.jetbrains.annotations.NotNull;

import cn.hutool.core.util.StrUtil;
import io.nekohasekai.sagernet.fmt.AbstractBean;
import io.nekohasekai.sagernet.fmt.KryoConverters;

public class VLESSBean extends StandardV2RayBean {

    public String flow;

    @Override
    public void initializeDefaultValues() {
        super.initializeDefaultValues();

        if (StrUtil.isBlank(encryption)) {
            encryption = "none";
        }
        if (StrUtil.isBlank(flow)) {
            flow = "";
        }

    }

    @Override
    public void applyFeatureSettings(AbstractBean other) {
        super.applyFeatureSettings(other);
        if (!(other instanceof VLESSBean bean)) return;
        if (flow.endsWith("-udp443") && !StrUtil.isBlank(bean.flow) && !bean.flow.endsWith("-udp443")) {
            bean.flow = flow; // keep -udp443
        }
    }

    @NotNull
    @Override
    public VLESSBean clone() {
        return KryoConverters.deserialize(new VLESSBean(), KryoConverters.serialize(this));
    }

    public static final Creator<VLESSBean> CREATOR = new CREATOR<VLESSBean>() {
        @NonNull
        @Override
        public VLESSBean newInstance() {
            return new VLESSBean();
        }

        @Override
        public VLESSBean[] newArray(int size) {
            return new VLESSBean[size];
        }
    };
}

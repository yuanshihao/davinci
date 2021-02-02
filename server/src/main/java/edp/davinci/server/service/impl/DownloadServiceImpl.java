/*
 * <<
 *  Davinci
 *  ==
 *  Copyright (C) 2016 - 2020 EDP
 *  ==
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *        http://www.apache.org/licenses/LICENSE-2.0
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *  >>
 *
 */

package edp.davinci.server.service.impl;

import edp.davinci.commons.util.StringUtils;
import edp.davinci.core.dao.entity.DownloadRecord;
import edp.davinci.core.enums.DownloadRecordStatusEnum;
import edp.davinci.server.component.excel.ExecutorUtils;
import edp.davinci.server.component.excel.MsgWrapper;
import edp.davinci.server.component.excel.WidgetContext;
import edp.davinci.server.component.excel.WorkBookContext;
import edp.davinci.server.dao.DownloadRecordExtendMapper;
import edp.davinci.server.dao.UserExtendMapper;
import edp.davinci.server.dto.view.DownloadViewExecuteParam;
import edp.davinci.server.enums.ActionEnum;
import edp.davinci.server.enums.DownloadType;
import edp.davinci.server.exception.UnAuthorizedException;
import edp.davinci.core.dao.entity.User;
import edp.davinci.server.service.DownloadService;
import edp.davinci.server.enums.LogNameEnum;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;


/**
 * Created by IntelliJ IDEA.
 *
 * @Author daemon
 * @Date 19/5/28 10:04
 * To change this template use File | Settings | File Templates.
 */
@Service
@Slf4j
public class DownloadServiceImpl extends DownloadCommonService implements DownloadService {

    private static final Logger downloadLogger = LoggerFactory.getLogger(LogNameEnum.BUSINESS_DOWNLOAD.getName());

    @Autowired
    private DownloadRecordExtendMapper downloadRecordExtendMapper;

    @Autowired
    private UserExtendMapper userMapper;

    @Override
    public List<DownloadRecord> queryDownloadRecordPage(Long userId) {
        return downloadRecordExtendMapper.getDownloadRecordsByUser(userId);
    }

    @Override
    public DownloadRecord downloadById(Long id, String token) throws UnAuthorizedException {
        if (StringUtils.isEmpty(token)) {
            throw new UnAuthorizedException();
        }

        String username = tokenUtils.getUsername(token);
        if (StringUtils.isEmpty(username)) {
            throw new UnAuthorizedException();
        }

        User user = userMapper.selectByUsername(username);
        if (null == user) {
            throw new UnAuthorizedException();
        }

        DownloadRecord record = downloadRecordExtendMapper.selectByPrimaryKey(id);

        if (!record.getUserId().equals(user.getId())) {
            throw new UnAuthorizedException();
        }

        record.setLastDownloadTime(new Date());
        record.setStatus(DownloadRecordStatusEnum.DOWNLOADED.getStatus());
        downloadRecordExtendMapper.updateById(record);
        return record;
    }

    @Override
    public Boolean submit(DownloadType type, Long id, User user, List<DownloadViewExecuteParam> params) {
        try {
            List<WidgetContext> widgetList = getWidgetContexts(type, id, user, params);
            DownloadRecord record = new DownloadRecord();
            record.setName(getDownloadFileName(type, id));
            record.setUserId(user.getId());
            record.setCreateTime(new Date());
            record.setStatus(DownloadRecordStatusEnum.PROCESSING.getStatus());
            downloadRecordExtendMapper.insertSelective(record);
            MsgWrapper wrapper = new MsgWrapper(record, ActionEnum.DOWNLOAD, record.getId());

            WorkBookContext workBookContext = WorkBookContext.builder()
                    .wrapper(wrapper)
                    .widgets(widgetList)
                    .user(user)
                    .resultLimit(resultLimit)
                    .taskKey("DownloadTask_" + id)
                    .customLogger(downloadLogger)
                    .build();

            ExecutorUtils.submitWorkbookTask(workBookContext, downloadLogger);
            log.info("Download task submit:{}", wrapper);
        } catch (Exception e) {
            log.error("Submit download task error", e);
            return false;
        }
        return true;
    }
}
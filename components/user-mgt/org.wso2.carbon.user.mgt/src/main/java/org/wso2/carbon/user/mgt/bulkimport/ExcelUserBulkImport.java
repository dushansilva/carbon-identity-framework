/*
 * Copyright (c) 2007 WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 *  WSO2 Inc. licenses this file to you under the Apache License,
 *  Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.wso2.carbon.user.mgt.bulkimport;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.wso2.carbon.CarbonConstants;
import org.wso2.carbon.identity.core.util.IdentityIOStreamUtils;
import org.wso2.carbon.user.core.UserStoreManager;
import org.wso2.carbon.user.core.util.UserCoreUtil;
import org.wso2.carbon.user.mgt.UserMgtConstants;
import org.wso2.carbon.user.mgt.bulkimport.util.JSONConverter;
import org.wso2.carbon.user.mgt.common.UserAdminException;

import java.io.InputStream;

public class ExcelUserBulkImport extends UserBulkImport {

    private static final Log log = LogFactory.getLog(ExcelUserBulkImport.class);

    private BulkImportConfig config;

    public ExcelUserBulkImport(BulkImportConfig config) {

        super();
        this.config = config;
    }

    public void addUserList(UserStoreManager userStore) throws UserAdminException {

        Workbook wb = this.createWorkbook();
        Sheet sheet = wb.getSheet(wb.getSheetName(0));
        userStoreDomain = config.getUserStoreDomain();

        if (sheet == null || sheet.getLastRowNum() == -1) {
            throw new UserAdminException("The first sheet is empty");
        }
        int limit = sheet.getLastRowNum();
        boolean isDuplicate = false;
        boolean fail = false;
        for (int i = 1; i < limit + 1; i++) {
            Row row = sheet.getRow(i);
            Cell cell = row.getCell(0);
            String userName = cell.getStringCellValue();

            int index;
            index = userName.indexOf(CarbonConstants.DOMAIN_SEPARATOR);
            if (index > 0) {
                String domainFreeName = userName.substring(index + 1);
                userName = UserCoreUtil.addDomainToName(domainFreeName, userStoreDomain);
            } else {
                userName = UserCoreUtil.addDomainToName(userName, userStoreDomain);
            }

            if (userName != null && userName.trim().length() > 0) {
                try {
                    if (!userStore.isExistingUser(userName)) {
                        userStore.addUser(userName, null, null, null, null, true);
                        successCount++;
                        if (log.isDebugEnabled()) {
                            log.debug("User import successful - Username : " + userName);
                        }
                    } else {
                        duplicateCount++;
                        duplicateUsers.add(userName);
                        isDuplicate = true;
                        log.error("User import unsuccessful - Username : " + userName + " - Error: Duplicate user");
                        duplicateUsers.add(userName);
                    }
                } catch (Exception e) {
                    fail = true;
                    failCount++;
                    log.error("User import unsuccessful - Username : " + userName + " - Error: " +
                            e.getMessage());
                    errorUsersMap.put(userName, e.getMessage());
                }
            }
        }

        String summeryLog = super.buildBulkImportSummery();
        log.info(summeryLog);

        JSONConverter jsonConverter = new JSONConverter();
        String importedUsers = jsonConverter.xlsToJSON(sheet);

        auditLog.info(String.format(UserMgtConstants.AUDIT_LOG_FORMAT, tenantUser, UserMgtConstants.OPERATION_NAME,
                userStoreDomain, importedUsers, summeryLog));

        if (fail || isDuplicate) {
            String messageBuilder = "Bulk User Import was completed with Errors. " + "Success count : " +
                    successCount + " Failed Count : " + failCount + " Duplicate Count : " + duplicateCount + ". ";
            throw new UserAdminException(messageBuilder);
        }
    }

    private Workbook createWorkbook() throws UserAdminException {

        String filename = config.getFileName();
        InputStream ins = config.getInStream();
        Workbook wb;
        try {
            if (filename.endsWith(".xlsx")) {
                wb = new XSSFWorkbook(ins);
            } else {
                POIFSFileSystem fs = new POIFSFileSystem(ins);
                wb = new HSSFWorkbook(fs);
            }
        } catch (Exception e) {
            log.error("Bulk import failed" + e.getMessage(), e);
            throw new UserAdminException("Bulk import failed" + e.getMessage(), e);
        } finally {
            IdentityIOStreamUtils.closeInputStream(ins);
        }
        return wb;
    }

}

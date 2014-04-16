package inm.macro.ide.ci

import groovy.transform.*

@Singleton
final class Config {
    final Map params = [
        BASELINE_PREFIX:            "MACRO_IDE_CI_BUILD",
        HTTP_PUBLISH_DIR:           "C:\\Wallace\\httpd",
        MAINBUILD_STREAM_NAME:      "INM_SR6.0-SP1_BUILDS",
        FEATURE_STREAM_PATH:        "C:\\CC\\INM_MACRO_IDE_SR6.0-SP1\\inm_sources_01\\Macro\\IDE",
        DEVELOPMENT_STREAM_PATH:    "C:\\CC\\qwei_INM_MACRO_IDE_SR6.0-SP1\\inm_sources_01\\Macro\\IDE",
        REPOSITORY_PATH:            "releng\\com.tellabs.inm.macro.updatesite\\target\\repository",
        STANDALONE_PRODUCT_PATH:    "releng\\com.tellabs.inm.macro.product\\target\\products",
        IDE_DOC_PATH:               "doc",
        IDE_USER_GUIDE_DOC:         "Macro_IDE_user_guide.doc",
        IDE_USER_GUIDE_PDF_CONVERTION: "true",
        IDE_USER_GUIDE_PDF:         "Macro_IDE_user_guide.pdf",
        INMTOOLS_BAT:               "C:\\InmTools\\INM-SR6.0.bat",
        IDE_BUILD_ONLINE_MODE:      true,
        DEBUG_LOGGING:              true,
        CODE_SUBMIT:                true,
        NEW_BUILD_LABEL_FOR_ONLY_IDE_CHNAGES: false,
        NEW_SUBMIT_CAUSE_TITLE:     "New submits:",
        NEW_SUBMIT_DELIMITER:       "%%",
        REMOTE_IM_DIR:              "\\\\cnsh2wfs02\\dfs\\proj\\shanghai\\8000\\local\\DO_NOT_TOUCH\\MACRO_IM_Builds",
        REMOTE_IM_READY_FLAG_FILE:  "done", 
        LOCAL_IM_DIR:               "plugins\\com.tellabs.inm.macro\\json",
        IM_FILE_LIST:               ['allinternaltokens.txt', 'alltokens.txt', 'networkobject.json', 'system.json', 'visual.json'],
        IM_CHECK_JOB:               "MACRO_IDE_IM_CHECK",
        BUILD_TRIGGER_JOB:          "MACRO_IDE_build_trigger_check",
        IDE_BUILD_JOB:              "MACRO_IDE",
        IDE_BUILD_PARAM_IM_VERSION: "NEW_IM_VERSION",
        IDE_BUILD_PARAM_NEW_SUBMIT: "NEW_IDE_CODE_SUBMITS",
        MONITOR_JOB:                "Exception_listener"
    ].asImmutable()

    final Map ccCmds = [
        GET_VOB_NAME:               'cleartool lsstream -fmt "%[project]Xp" -cview',
        UPDATE_VIEW:                'cleartool update -force .',
        GET_ALL_ACTIVITIES:         'cleartool lsactivity -s -cview ',
        CHECK_ACTIVITY:             'cleartool desc -fmt "%[locked]p,%[MKACTIVITY]NSa,%[SUBMITTED]NSa,%[PRESUBMITTED]NSa" activity:',
        GET_ACTIVITY_HEADLINE:      'cleartool lsact -fmt "%[headline]p"',
        GET_CONTRIB_ACTIVITY:       'cleartool lsact -fmt "%[contrib_acts]XCp" activity:',
        GET_ACTIVITY_CHANGESET:     'cleartool lsact -fmt "%[versions]Cp"  activity:',
        GET_OWN_BUILD_BASELINE:     'cleartool lsstream -fmt "%[found_bls]XCp"',
        GET_MAINBUILD_BASELINE:     'cleartool lsstream -fmt "%[rec_bls]XCp"',
        REBASE:                     'cleartool rebase -recommended',
        REBASE_COMPLETE:            'cleartool rebase -complete',
        MKBASELINE:                 'cleartool mkbl -nc -component Release@',
        LABEL_BASELINE:             'cleartool chbl -full ',
        RECOMMEND_BASELINE:         'cleartool chstream -recommend ',
        GET_STREAM_NAME:            'cleartool  lsstream -s -cview',
        GET_BASELINE_INFO:          'cleartool lsbl -long -cview -component Macro@'
    ].asImmutable()
}
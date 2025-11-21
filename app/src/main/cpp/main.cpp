//=============================================================================
//
//  Copyright (c) Qualcomm Technologies, Inc. and/or its subsidiaries.
//  All Rights Reserved.
//  Confidential and Proprietary - Qualcomm Technologies, Inc.
//
//=============================================================================

#include <chrono>
#include <exception>
#include <fstream>
#include <iomanip>
#include <iostream>
#include <string>
#include <unordered_map>
#include <unordered_set>
#include <vector>

#include "Logging.hpp"

#include "main.hpp"

std::string g_strAnswer;

void queryCallback(const char *responseStr, const GenieDialog_SentenceCode_t sentenceCode, const void *)
{
#if 0
	switch( sentenceCode )
	{
		case GENIE_DIALOG_SENTENCE_COMPLETE:	LoggingDebug("GENIE_DIALOG_SENTENCE_COMPLETE:\n");		break;
		case GENIE_DIALOG_SENTENCE_BEGIN:		LoggingDebug("GENIE_DIALOG_SENTENCE_BEGIN:\n");			break;
		case GENIE_DIALOG_SENTENCE_CONTINUE:	LoggingDebug("GENIE_DIALOG_SENTENCE_CONTINUE:\n");		break;
		case GENIE_DIALOG_SENTENCE_END:			LoggingDebug("GENIE_DIALOG_SENTENCE_END:\n");			break;
		case GENIE_DIALOG_SENTENCE_ABORT:		LoggingDebug("GENIE_DIALOG_SENTENCE_ABORT:\n");			break;
		default:								LoggingDebug("default:\n");								break;
	}
#endif

	if (sentenceCode == GENIE_DIALOG_SENTENCE_ABORT || sentenceCode == GENIE_DIALOG_SENTENCE_END)
		return;

	if (responseStr)
	{
		if (sentenceCode == GENIE_DIALOG_SENTENCE_BEGIN)
			g_strAnswer.clear();
		// else													g_strAnswer += "_";
		g_strAnswer += responseStr;
		// LoggingMax("\tresponseStr = [ %s ]\n", responseStr);
	}
}

DsConfig::DsConfig()
{
	m_handle = NULL;
}

DsConfig::~DsConfig()
{
	UnInit();
}

int DsConfig::Init(char *szConfigPath)
{
	if (m_handle != NULL)
	{
		LoggingWarning("Allready Init !!!\n");
		return 0;
	}

	std::string strConfig;
	std::getline(std::ifstream(szConfigPath), strConfig, '\0');

	LoggingDebug("<<<<<<<<<< Call GenieDialogConfig_createFromJson(\"%s\", &m_handle);\n", szConfigPath);
	int32_t status = GenieDialogConfig_createFromJson(strConfig.c_str(), &m_handle);
	LoggingDebug(">>>>>>>>>> %d = GenieDialogConfig_createFromJson(\"%s\", &m_handle);\n", status, szConfigPath);
	if ((GENIE_STATUS_SUCCESS != status) || m_handle == NULL)
	{
		LoggingMax("m_handle = %p\n", m_handle);
		m_handle = NULL;
		LoggingCritic("Failed to create the dialog config !!! : status = %d, szConfigPath = [ %s ]\n", status, szConfigPath);
		return -1;
	}

	return 0;
}

void DsConfig::UnInit()
{
	if (m_handle == NULL)
	{
		LoggingWarning("Not Init !!!\n");
		return;
	}

#ifdef NO_LOGGING
	GenieDialogConfig_free(m_handle);
#else
	LoggingMax("<<<<<<<<<< Call GenieDialogConfig_free(m_handle);\n");
	int32_t status = GenieDialogConfig_free(m_handle);
	LoggingMax(">>>>>>>>>> %d = GenieDialogConfig_free(m_handle);\n", status);
	if (GENIE_STATUS_SUCCESS != status)
	{
		LoggingCritic("Failed to free the dialog config. : status = %d, m_handle = %p\n", status, m_handle);
	}
#endif

	m_handle = NULL;
}

DsDialog::DsDialog()
{
	m_blReset = false;
	m_handle = NULL;
}

DsDialog::~DsDialog()
{
	UnInit();
}

int DsDialog::Init(DsConfig &config)
{
	if (m_handle != NULL)
	{
		LoggingWarning("Allready Init !!!\n");
		return 0;
	}

	LoggingDebug("<<<<<<<<<< Call GenieDialog_create(config.GetHandle(), &m_handle);\n");
	int32_t status = GenieDialog_create(config.GetHandle(), &m_handle);
	LoggingDebug(">>>>>>>>>> %d = GenieDialog_create(config.GetHandle(), &m_handle);\n", status);
	if ((GENIE_STATUS_SUCCESS != status) || m_handle == NULL)
	{
		m_handle = NULL;
		LoggingCritic("Failed to create the dialog !!! : status = %d\n", status);
		return -1;
	}

	return 0;
}

void DsDialog::UnInit()
{
	if (m_handle == NULL)
	{
		LoggingWarning("Not Init !!!\n");
		return;
	}

#ifdef NO_LOGGING
	GenieDialog_free(m_handle);
#else
	LoggingMax("<<<<<<<<<< Call GenieDialog_free(m_handle);\n");
	int32_t status = GenieDialog_free(m_handle);
	LoggingMax(">>>>>>>>>> %d = GenieDialog_free(m_handle);\n", status);
	if (GENIE_STATUS_SUCCESS != status)
	{
		LoggingCritic("Failed to free the dialog !!! : status = %d\n", status);
	}
#endif

	m_handle = NULL;
}

std::string DsDialog::Query(const std::string strPrompt)
{
	if (m_handle == NULL)
	{
		LoggingWarning("Not Init !!!\n");
		return "";
	}

	if (m_blReset)
	{
		// LoggingMax("<<<<<<<<<< Call enieDialog_reset(m_handle);\n");
		int32_t status = GenieDialog_reset(m_handle);
		// LoggingMax(">>>>>>>>>> %d = enieDialog_reset(m_handle);\n", status);
	}

	int32_t status = GenieDialog_query(m_handle, strPrompt.c_str(), GenieDialog_SentenceCode_t::GENIE_DIALOG_SENTENCE_COMPLETE, queryCallback, nullptr);
	if (GENIE_STATUS_SUCCESS != status)
	{
		LoggingCritic("Failed to query !!! : g_strAnswer = %s\n", g_strAnswer.c_str());
	}

	m_blReset = true;

	return g_strAnswer;
}

/*
int main(int argc, char** argv)
{
	if( !parseCommandLineInput(argc, argv) )
	{
		return EXIT_FAILURE;
	}

	std::cout << "Using libGenie.so version " << Genie_getApiMajorVersion() << "." << Genie_getApiMinorVersion() << "." << Genie_getApiPatchVersion() << "\n" << std::endl;

	try
	{
		DsDialog dialog{DsConfig(strConfigPath)};

		std::cout << "[PROMPT]: " << strPrompt.c_str() << std::endl;
		std::cout << std::endl;
		dialog.query(strPrompt);
		std::cout << std::endl;
	}
	catch( const std::exception& e )
	{
		std::cerr << e.what() << std::endl;
		return EXIT_FAILURE;
	}

	return 0;
}*/

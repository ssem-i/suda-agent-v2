#pragma once

#include <string>

#include <GenieCommon.h>
#include <GenieDialog.h>

class DsConfig
{
	public:
		DsConfig();
		~DsConfig();

		int Init(char *szConfigPath);
		void UnInit();

		GenieDialogConfig_Handle_t GetHandle()	{	return m_handle;	}

	private:
		GenieDialogConfig_Handle_t m_handle;
};

class DsDialog
{
	public:
		DsDialog();
		~DsDialog();

		int Init(DsConfig &config);
		void UnInit();

		std::string Query(std::string strPrompt);

	private:
		bool	m_blReset;
		GenieDialog_Handle_t m_handle;
};

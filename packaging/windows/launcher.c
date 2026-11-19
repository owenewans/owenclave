#define UNICODE
#define _UNICODE
#include <windows.h>
#include <stdio.h>
#include <wchar.h>

static int file_exists(const wchar_t *path) {
    DWORD attr = GetFileAttributesW(path);
    return (attr != INVALID_FILE_ATTRIBUTES && !(attr & FILE_ATTRIBUTE_DIRECTORY));
}

static int dir_exists(const wchar_t *path) {
    DWORD attr = GetFileAttributesW(path);
    return (attr != INVALID_FILE_ATTRIBUTES && (attr & FILE_ATTRIBUTE_DIRECTORY));
}

int wmain(int argc, wchar_t *argv[]) {
    wchar_t exePath[MAX_PATH * 4];
    DWORD len = GetModuleFileNameW(NULL, exePath, MAX_PATH * 4);
    if (len == 0 || len >= MAX_PATH * 4) {
        return 1;
    }

    wchar_t *lastSlash = wcsrchr(exePath, L'\\');
    if (!lastSlash) lastSlash = wcsrchr(exePath, L'/');
    if (lastSlash) *lastSlash = L'\0';

    wchar_t appHome[MAX_PATH * 4];
    wcscpy(appHome, exePath);

    wchar_t checkAppDir[MAX_PATH * 4];
    swprintf(checkAppDir, MAX_PATH * 4, L"%ls\\app", appHome);
    if (!dir_exists(checkAppDir)) {
        wchar_t parentDir[MAX_PATH * 4];
        wcscpy(parentDir, appHome);
        wchar_t *parentSlash = wcsrchr(parentDir, L'\\');
        if (!parentSlash) parentSlash = wcsrchr(parentDir, L'/');
        if (parentSlash) {
            *parentSlash = L'\0';
            swprintf(checkAppDir, MAX_PATH * 4, L"%ls\\app", parentDir);
            if (dir_exists(checkAppDir)) {
                wcscpy(appHome, parentDir);
            }
        }
    }

    SetCurrentDirectoryW(appHome);

    int hasCliArgs = 0;
    for (int i = 1; i < argc; i++) {
        if (argv[i][0] == L'-' && wcscmp(argv[i], L"--legacy-ui") != 0) {
            hasCliArgs = 1;
            break;
        }
    }
    wchar_t javaExe[MAX_PATH * 4];
    swprintf(
        javaExe,
        MAX_PATH * 4,
        L"%ls\\runtime\\bin\\%ls",
        appHome,
        hasCliArgs ? L"java.exe" : L"javaw.exe"
    );
    if (!file_exists(javaExe)) {
        swprintf(
            javaExe,
            MAX_PATH * 4,
            L"%ls\\lib\\runtime\\bin\\%ls",
            appHome,
            hasCliArgs ? L"java.exe" : L"javaw.exe"
        );
    }
    if (!file_exists(javaExe)) {
        wcscpy(javaExe, hasCliArgs ? L"java.exe" : L"javaw.exe");
    }

    wchar_t cmdLine[32768];
    swprintf(
        cmdLine,
        32768,
        L"\"%ls\" \"-Djpackage.app-path=%ls\\Owenclave.exe\" \"-Dowenclave.gojni.path=%ls\\bin\\gojni.dll\" -cp \"%ls\\app\\*\" io.nekohasekai.sagernet.desktop.MainKt",
        javaExe,
        appHome,
        appHome,
        appHome
    );

    for (int i = 1; i < argc; i++) {
        wcscat(cmdLine, L" \"");
        wcscat(cmdLine, argv[i]);
        wcscat(cmdLine, L"\"");
    }

    STARTUPINFOW si;
    PROCESS_INFORMATION pi;
    ZeroMemory(&si, sizeof(si));
    si.cb = sizeof(si);
    ZeroMemory(&pi, sizeof(pi));

    DWORD creationFlags = hasCliArgs ? 0 : CREATE_NO_WINDOW;
    if (!CreateProcessW(
            NULL,
            cmdLine,
            NULL,
            NULL,
            TRUE,
            creationFlags,
            NULL,
            appHome,
            &si,
            &pi)) {
        fwprintf(stderr, L"Failed to launch %ls (error %lu)\n", javaExe, GetLastError());
        return 1;
    }

    if (hasCliArgs) {
        WaitForSingleObject(pi.hProcess, INFINITE);
        DWORD exitCode = 0;
        GetExitCodeProcess(pi.hProcess, &exitCode);
        CloseHandle(pi.hProcess);
        CloseHandle(pi.hThread);
        return (int)exitCode;
    }

    CloseHandle(pi.hProcess);
    CloseHandle(pi.hThread);
    return 0;
}

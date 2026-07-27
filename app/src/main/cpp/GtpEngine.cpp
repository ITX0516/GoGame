#include "GtpEngine.h"
#include <android/log.h>
#include <sstream>
#include <algorithm>
#include <cctype>

#define LOG_TAG "GtpEngine"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

GtpEngine::GtpEngine()
    : running_(false)
    , ready_(false)
    , boardSize_(19)
    , komi_(6.5f)
    , moveCount_(0) {
}

GtpEngine::~GtpEngine() {
    stop();
}

bool GtpEngine::start() {
    if (running_) return true;

    running_ = true;
    engineThread_ = std::thread(&GtpEngine::engineLoop, this);
    ready_ = true;
    LOGD("GTP Engine started (mock mode)");
    return true;
}

void GtpEngine::stop() {
    running_ = false;
    ready_ = false;
    queueCv_.notify_all();

    if (engineThread_.joinable()) {
        engineThread_.join();
    }
    LOGD("GTP Engine stopped");
}

bool GtpEngine::isReady() const {
    return ready_ && running_;
}

void GtpEngine::sendCommand(const std::string& command) {
    {
        std::lock_guard<std::mutex> lock(queueMutex_);
        commandQueue_.push(command);
    }
    queueCv_.notify_one();
}

void GtpEngine::setResponseCallback(ResponseCallback callback) {
    std::lock_guard<std::mutex> lock(callbackMutex_);
    responseCallback_ = std::move(callback);
}

std::string GtpEngine::sendCommandSync(const std::string& command) {
    return processCommand(command);
}

void GtpEngine::engineLoop() {
    while (running_) {
        std::string command;
        {
            std::unique_lock<std::mutex> lock(queueMutex_);
            queueCv_.wait(lock, [this] { return !commandQueue_.empty() || !running_; });

            if (!running_) break;
            if (commandQueue_.empty()) continue;

            command = commandQueue_.front();
            commandQueue_.pop();
        }

        std::string response = processCommand(command);

        std::lock_guard<std::mutex> lock(callbackMutex_);
        if (responseCallback_) {
            responseCallback_(response);
        }
    }
}

std::string GtpEngine::processCommand(const std::string& input) {
    std::string trimmed = input;
    trimmed.erase(trimmed.find_last_not_of(" \n\r\t") + 1);
    trimmed.erase(0, trimmed.find_first_not_of(" \n\r\t"));

    if (trimmed.empty()) {
        return "";
    }

    int id = -1;
    size_t pos = 0;

    if (std::isdigit(trimmed[0])) {
        size_t digitEnd = 0;
        while (digitEnd < trimmed.size() && std::isdigit(trimmed[digitEnd])) {
            digitEnd++;
        }
        id = std::stoi(trimmed.substr(0, digitEnd));
        pos = digitEnd;
        while (pos < trimmed.size() && trimmed[pos] == ' ') {
            pos++;
        }
    }

    size_t spacePos = trimmed.find(' ', pos);
    std::string cmd = trimmed.substr(pos, spacePos - pos);
    std::string args = (spacePos != std::string::npos) ? trimmed.substr(spacePos + 1) : "";

    std::transform(cmd.begin(), cmd.end(), cmd.begin(), ::tolower);

    LOGD("Processing command: %s, args: %s", cmd.c_str(), args.c_str());

    std::string result;

    if (cmd == "protocol_version") {
        result = processProtocolVersion(args);
    } else if (cmd == "name") {
        result = processName(args);
    } else if (cmd == "version") {
        result = processVersion(args);
    } else if (cmd == "known_command") {
        result = processKnownCommand(args);
    } else if (cmd == "list_commands") {
        result = processListCommands(args);
    } else if (cmd == "quit") {
        result = processQuit(args);
    } else if (cmd == "boardsize") {
        result = processBoardsize(args);
    } else if (cmd == "clear_board") {
        result = processClearBoard(args);
    } else if (cmd == "komi") {
        result = processKomi(args);
    } else if (cmd == "play") {
        result = processPlay(args);
    } else if (cmd == "genmove") {
        result = processGenmove(args);
    } else if (cmd == "undo") {
        result = processUndo(args);
    } else if (cmd == "time_settings") {
        result = processTimeSettings(args);
    } else if (cmd == "time_left") {
        result = processTimeLeft(args);
    } else if (cmd == "final_score") {
        result = processFinalScore(args);
    } else if (cmd == "showboard") {
        result = processShowBoard(args);
    } else {
        result = makeResponse(false, "unknown command");
    }

    if (id >= 0) {
        std::string idStr = std::to_string(id);
        size_t insertPos = 1;
        result.insert(insertPos, idStr);
    }

    return result;
}

std::string GtpEngine::makeResponse(bool success, const std::string& content) {
    std::string response = success ? "=" : "?";
    if (!content.empty()) {
        response += " " + content;
    }
    response += "\n\n";
    return response;
}

std::string GtpEngine::processProtocolVersion(const std::string& args) {
    return makeResponse(true, "2");
}

std::string GtpEngine::processName(const std::string& args) {
    return makeResponse(true, "GoGame Mock Engine");
}

std::string GtpEngine::processVersion(const std::string& args) {
    return makeResponse(true, "1.0");
}

std::string GtpEngine::processKnownCommand(const std::string& args) {
    std::string lowerArgs = args;
    std::transform(lowerArgs.begin(), lowerArgs.end(), lowerArgs.begin(), ::tolower);

    static const char* knownCmds[] = {
        "protocol_version", "name", "version", "known_command", "list_commands",
        "quit", "boardsize", "clear_board", "komi", "play", "genmove",
        "undo", "time_settings", "time_left", "final_score", "showboard"
    };

    for (const char* cmd : knownCmds) {
        if (lowerArgs == cmd) {
            return makeResponse(true, "true");
        }
    }
    return makeResponse(true, "false");
}

std::string GtpEngine::processListCommands(const std::string& args) {
    std::string cmds =
        "protocol_version\n"
        "name\n"
        "version\n"
        "known_command\n"
        "list_commands\n"
        "quit\n"
        "boardsize\n"
        "clear_board\n"
        "komi\n"
        "play\n"
        "genmove\n"
        "undo\n"
        "time_settings\n"
        "time_left\n"
        "final_score\n"
        "showboard";
    return makeResponse(true, cmds);
}

std::string GtpEngine::processQuit(const std::string& args) {
    running_ = false;
    return makeResponse(true, "");
}

std::string GtpEngine::processBoardsize(const std::string& args) {
    try {
        int size = std::stoi(args);
        if (size >= 2 && size <= 25) {
            boardSize_ = size;
            return makeResponse(true, "");
        }
        return makeResponse(false, "boardsize not allowed");
    } catch (...) {
        return makeResponse(false, "syntax error");
    }
}

std::string GtpEngine::processClearBoard(const std::string& args) {
    moveCount_ = 0;
    return makeResponse(true, "");
}

std::string GtpEngine::processKomi(const std::string& args) {
    try {
        komi_ = std::stof(args);
        return makeResponse(true, "");
    } catch (...) {
        return makeResponse(false, "syntax error");
    }
}

std::string GtpEngine::processPlay(const std::string& args) {
    if (args.empty()) {
        return makeResponse(false, "syntax error");
    }
    moveCount_++;
    return makeResponse(true, "");
}

std::string GtpEngine::processGenmove(const std::string& args) {
    int x = moveCount_ % boardSize_;
    int y = (moveCount_ / boardSize_) % boardSize_;
    moveCount_++;

    char col = 'A' + (x >= 8 ? x + 1 : x);
    int row = y + 1;

    std::ostringstream oss;
    oss << col << row;
    return makeResponse(true, oss.str());
}

std::string GtpEngine::processUndo(const std::string& args) {
    if (moveCount_ > 0) {
        moveCount_--;
        return makeResponse(true, "");
    }
    return makeResponse(false, "cannot undo");
}

std::string GtpEngine::processTimeSettings(const std::string& args) {
    return makeResponse(true, "");
}

std::string GtpEngine::processTimeLeft(const std::string& args) {
    return makeResponse(true, "");
}

std::string GtpEngine::processFinalScore(const std::string& args) {
    return makeResponse(true, "0");
}

std::string GtpEngine::processShowBoard(const std::string& args) {
    return makeResponse(true, "mock board");
}

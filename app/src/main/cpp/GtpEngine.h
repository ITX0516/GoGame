#ifndef GOGAME_GTP_ENGINE_H
#define GOGAME_GTP_ENGINE_H

#include <string>
#include <queue>
#include <mutex>
#include <condition_variable>
#include <thread>
#include <functional>

class GtpEngine {
public:
    using ResponseCallback = std::function<void(const std::string&)>;

    GtpEngine();
    ~GtpEngine();

    bool start();
    void stop();
    bool isReady() const;

    void sendCommand(const std::string& command);
    void setResponseCallback(ResponseCallback callback);

    std::string sendCommandSync(const std::string& command);

private:
    void engineLoop();
    std::string processCommand(const std::string& command);

    std::string processProtocolVersion(const std::string& args);
    std::string processName(const std::string& args);
    std::string processVersion(const std::string& args);
    std::string processKnownCommand(const std::string& args);
    std::string processListCommands(const std::string& args);
    std::string processQuit(const std::string& args);
    std::string processBoardsize(const std::string& args);
    std::string processClearBoard(const std::string& args);
    std::string processKomi(const std::string& args);
    std::string processPlay(const std::string& args);
    std::string processGenmove(const std::string& args);
    std::string processUndo(const std::string& args);
    std::string processTimeSettings(const std::string& args);
    std::string processTimeLeft(const std::string& args);
    std::string processFinalScore(const std::string& args);
    std::string processShowBoard(const std::string& args);

    std::string makeResponse(bool success, const std::string& content);

    bool running_;
    bool ready_;
    int boardSize_;
    float komi_;

    std::queue<std::string> commandQueue_;
    std::mutex queueMutex_;
    std::condition_variable queueCv_;

    std::thread engineThread_;
    ResponseCallback responseCallback_;
    std::mutex callbackMutex_;

    int moveCount_;
};

#endif

import com.mpatric.mp3agic.InvalidDataException;
import com.mpatric.mp3agic.UnsupportedTagException;
import javazoom.jl.decoder.*;
import javazoom.jl.player.AudioDevice;
import javazoom.jl.player.FactoryRegistry;
import support.PlayerWindow;
import support.Song;
import java.io.File;
import javax.swing.*;
import java.time.LocalDateTime;
import java.time.Duration;
import java.io.ByteArrayInputStream;
import java.util.Collections;
import java.util.List;




import javax.swing.event.MouseInputAdapter;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.MouseEvent;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.locks.ReentrantLock;
import javax.swing.JButton;

public class Player {

    /**
     * The MPEG audio bitstream.
     */
    private Bitstream bitstream;
    //private Bitstream pausedBitsream;
    /**
     * The MPEG audio decoder.
     */
    private Decoder decoder;
    /**
     * The AudioDevice where audio samples are written to.
     */
    private AudioDevice device;

    private PlayerWindow window;

    private final String windowTitle = "Spotify dos Cria";

    // todas as músicas na lista de reprodução
    private Song[] song;
    private String[][] songInfo;
    private Song[] regularSong;
    private String[][] regularSongInfo;

    // índice da música
    private int currentSongIndex;
    private int currentFrame = 0;
    private int pausedFrame = 0;
    private int pausedsongindex = 0;

    // lock para bitstream
    private ReentrantLock bitstreamLock = new ReentrantLock();

    // booleano que muda se a música tiver tocando
    private boolean playing = false;

    // booleano que muda se a música tiver pausada
    private boolean paused = false;

    // booleano que muda se o scrubber for arrastado
    private boolean scrubberDragging = false;

    // booleano que muda se o shuffle for ativado
    private boolean shuffle = false;

    // booleano que muda se o loop for ativado
    private boolean loop = false;

    // thread para reprodução
    private Thread playThread = new Thread();


    // vamos reproduzir uma música selecionada no player
    private final ActionListener buttonListenerPlayNow = e -> {
        Thread playNowThread = new Thread(() -> {
            stopPlaying();
            startPlaying(getSelectedSongIndex());
        });

        playNowThread.start();
    };

    // para remover uma música que está selecionada no player
    private final ActionListener buttonListenerRemove = e -> {
        Thread removeThread = new Thread(() -> {
            int selectedSongIndex = getSelectedSongIndex();

            // caso a música seja deletada e esteja tocando, então:
            if (selectedSongIndex == currentSongIndex && playing) {
                stopPlaying();
                deleteSong(selectedSongIndex);

                if (song.length > selectedSongIndex) {
                    startPlaying(currentSongIndex);
                }
                else if (loop && song.length > 0){
                    startPlaying(0);
                }
            }

            else {
                deleteSong(selectedSongIndex);//COLOQUEI AQUI PRA REMOVER QUANDO A MÚSICA NÃO TIEVESSE TOCANDO TAMBÉM
            }

            // atualização do shuffle se for necessário atualizar
            if (song.length < 2) {
                EventQueue.invokeLater(() -> {
                    window.setEnabledShuffleButton(false);
                });
            }

            // atualização do botão loop se for necessário atualizar
            if (song.length == 0) {
                EventQueue.invokeLater(() -> {
                    window.setEnabledLoopButton(false);
                });
            }

            // atualização do anterior e próximo se for necessário atualizar
            if (playing) {
                EventQueue.invokeLater(() -> {
                    updatePreviousAndNextButtons();
                });
            }
        });

        removeThread.start();
    };


    private final ActionListener buttonListenerAddSong = e -> {
        Song addedSong = window.openFileChooser();

        String[] addedSongInfo = addedSong.getDisplayInfo();

        // aqui adicionamos as músicas ao array de Song
        if (song != null) {
            song = Arrays.copyOf(song, song.length + 1);
            song[song.length - 1] = addedSong;
        }

        else {
            song = new Song[]{addedSong};
        }

        // aqui adicionaremos as músicas ao array de regularSong
        if (regularSong != null) {
            regularSong = Arrays.copyOf(regularSong, regularSong.length + 1);
            regularSong[regularSong.length - 1] = addedSong;
        }

        else {
            regularSong = new Song[]{addedSong};
        }

        // aqui adicionamos as informações no array de songInfo
        if (songInfo != null) {
            songInfo = Arrays.copyOf(songInfo, songInfo.length + 1);
            songInfo[songInfo.length - 1] = addedSongInfo;
        }

        else {
            songInfo = new String[][]{addedSongInfo};
        }

        // aqui adicionamos as informações no array de regularSongInfo
        if (regularSongInfo != null){
            regularSongInfo = Arrays.copyOf(regularSongInfo, regularSongInfo.length + 1);
            regularSongInfo[regularSongInfo.length - 1] = addedSongInfo;
        }

        else {
            regularSongInfo = new String[][]{addedSongInfo};
        }

        // é feita a atualização da lista neste ponto
        window.setQueueList(songInfo);

        if (currentSongIndex <= song.length - 2 && playing) {
            window.setEnabledNextButton(true);
        }

        // atualização do shuffle se for necessário atualizar
        if (song.length == 2) {
            EventQueue.invokeLater(() -> {
                window.setEnabledShuffleButton(true);
            });
        }

        // atualização do botão loop se for necessário atualizar
        if (song.length == 1) {
            EventQueue.invokeLater(() -> {
                window.setEnabledLoopButton(true);
            });
        }

    };

    private final ActionListener buttonListenerPlayPause = e -> {

        if (paused) {
            // botão play e pause apenas pause
            window.setPlayPauseButtonIcon(1);

            // unpause na música
            paused = false;
            int selectedSongIndex = pausedsongindex;
            currentFrame = pausedFrame;
        }

        else {
            // botão play e pause apenas play
            window.setPlayPauseButtonIcon(0);
            pausedsongindex = currentSongIndex;
            // pause na música
            paused = true;
            pausedFrame = currentFrame;
        }
    };

    private final ActionListener buttonListenerStop = e -> {
        Thread stopThread = new Thread( () -> {
            stopPlaying();
        });

        stopThread.start();

    };

    private final ActionListener buttonListenerNext = e -> {
        Thread nextThread = new Thread(() -> {
            stopPlaying();
            startPlaying(currentSongIndex + 1);
        });

        nextThread.start();
    };

    private final ActionListener buttonListenerPrevious = e -> {
        Thread previousThread = new Thread (() -> {
            stopPlaying();

            startPlaying(currentSongIndex - 1);
        });

        previousThread.start();
    };

    private final ActionListener buttonListenerShuffle = e -> {
        Thread shuffleThread = new Thread (() -> {
            String currentSongId = songInfo[currentSongIndex][5];
            if (!shuffle){
                shuffle = true;

                // armazena a lista de reprodução atual
                regularSong = Arrays.copyOf(song, song.length);
                regularSongInfo = Arrays.copyOf(songInfo, songInfo.length);

                shuffleSongs();

                if (playing) {
                    // se tiver uma música sendo tocada, ela deve estar na cabeça da lista
                    for (int i = 0; i < songInfo.length; i++){
                        if (currentSongId == songInfo[i][5]){
                            String[] temp = songInfo [0];
                            Song temp2 = song[0];
                            //songInfo[0] = songInfo[1];
                            songInfo[0] = songInfo[i];
                            songInfo[i] = temp;


                            //song[0] = song[1];
                            song[0] = song[i];
                            song[i] = temp2;

                            break;
                        }
                    }

                    currentSongIndex = 0;
                }
            }

            else if (shuffle){
                shuffle = false;

                // volta a lista para o estado anterior
                song = Arrays.copyOf(regularSong, regularSong.length);
                songInfo = Arrays.copyOf(regularSongInfo, regularSongInfo.length);

                // atualizando o currentSongIndex
                if (playing) {
                    for (int i = 0; i < songInfo.length; i++){
                        if (currentSongId == songInfo[i][5]){
                            currentSongIndex = i;
                            break;
                        }
                    }
                }
            }

            // atualizando a interface
            EventQueue.invokeLater(() -> {
                window.setQueueList(songInfo);

                if (playing){
                    updatePreviousAndNextButtons();
                }
            });
        });

        shuffleThread.start();
    };
    private final ActionListener buttonListenerLoop = e -> {
        loop = !loop;
    };
    public final MouseInputAdapter scrubberMouseInputAdapter = new MouseInputAdapter() {

        private int songLength;

        private int scrubberTargetPoint;

        private boolean pausedPreviousState;

        void updateScrubber () {
            paused = true;
            scrubberDragging = false;

            // atualização do frame
            int scrubberCurrentPoint = window.getScrubberValue();

            // voltando a um momento anterior na música, criando uma bitstream pra fazer isso
            if (scrubberCurrentPoint >= scrubberTargetPoint) {
                bitstreamLock.lock();

                try {
                    if (bitstream != null) {
                        try {
                            bitstream.close();
                            device.close();
                        } catch (BitstreamException bitstreamException) {
                            bitstreamException.printStackTrace();
                        }
                    }

                    // criar a bitstream e o device
                    try {
                        device = FactoryRegistry.systemRegistry().createAudioDevice();
                        device.open(decoder = new Decoder());
                        bitstream = new Bitstream(song[currentSongIndex].getBufferedInputStream());
                    } catch (JavaLayerException | FileNotFoundException ex) {
                        throw new RuntimeException(ex);
                    }

                }

                finally {
                    bitstreamLock.unlock();
                }

                currentFrame = 0;


                // se quisermos selecionar um ponto no scrubber
                int newCurrentFrame = (int) (scrubberTargetPoint / song[currentSongIndex].getMsPerFrame());

                try {
                    skipToFrame(newCurrentFrame);
                } catch (BitstreamException ex) {
                    throw new RuntimeException(ex);
                }

                EventQueue.invokeLater(() -> {
                    window.setTime(scrubberTargetPoint, songLength);
                });

                paused = pausedPreviousState;

            }
        }

        @Override
        public void mouseReleased(MouseEvent e) {
            // atualizar a música pra o momento selecionado no scrubber
            Thread mouseReleasedThread = new Thread(() -> {
                updateScrubber();
            });

            mouseReleasedThread.start();
        }

        @Override
        public void mousePressed(MouseEvent e) {
            pausedPreviousState = paused;
            paused = true;
            scrubberTargetPoint = window.getScrubberValue();
            songLength = (int) song[currentSongIndex].getMsLength();
            window.setTime(scrubberTargetPoint, songLength);
            currentFrame = (int) (scrubberTargetPoint / song[currentSongIndex].getMsPerFrame());
        }

        @Override
        public void mouseDragged(MouseEvent e) {
            // fica atualizando o tempo baseado em aonde o scrubber tá
            paused = pausedPreviousState;
            scrubberDragging = true;
            scrubberTargetPoint = window.getScrubberValue();
            window.setTime(scrubberTargetPoint, songLength);
            currentFrame = (int) (scrubberTargetPoint / song[currentSongIndex].getMsPerFrame());
        }
    };

    public Player() {
        EventQueue.invokeLater(() -> window = new PlayerWindow(windowTitle,
                songInfo,
                buttonListenerPlayNow,
                buttonListenerRemove,
                buttonListenerAddSong,
                buttonListenerShuffle,
                buttonListenerPrevious,
                buttonListenerPlayPause,
                buttonListenerStop,
                buttonListenerNext,
                buttonListenerLoop,
                scrubberMouseInputAdapter)
        );
    }

    /**
     * @return False if there are no more frames to play.
     */
    private boolean playNextFrame() throws JavaLayerException {

        bitstreamLock.lock();

        try {
            if (device != null) {
                Header h = bitstream.readFrame();

                if (h == null) return false;

                SampleBuffer output = (SampleBuffer) decoder.decodeFrame(h, bitstream);
                device.write(output.getBuffer(), 0, output.getBufferLength());
                bitstream.closeFrame();

            }

            return true;

        } finally {
            bitstreamLock.unlock();
        }
    }

    /**
     * @return False if there are no more frames to skip.
     */
    private boolean skipNextFrame() throws BitstreamException {
        bitstreamLock.lock();
        try {
            Header h = bitstream.readFrame();
            if (h == null) return false;
            bitstream.closeFrame();
            currentFrame++;
            return true;
        }

        finally {
            bitstreamLock.unlock();
        }
    }

    /**
     * Skips bitstream to the target frame if the new frame is higher than the current one.
     *
     * @param newFrame Frame to skip to.
     */
    private void skipToFrame(int newFrame) throws BitstreamException {
        bitstreamLock.lock();
        try {
            if (newFrame > currentFrame) {
                int framesToSkip = newFrame - currentFrame;
                boolean condition = true;
                while (framesToSkip-- > 0 && condition) condition = skipNextFrame();
            }
        }

        finally {
            bitstreamLock.unlock();
        }
    }
    
    // caso queiramos interromper a reprodução:
    private void stopPlaying() {

        playThread.interrupt();

        if (bitstream != null) {
            try {
                bitstream.close();
                device.close();
            } catch (BitstreamException bitstreamException){
                bitstreamException.printStackTrace();
            }
        }

        EventQueue.invokeLater( () -> {
            // desabilitando o "play e pause"
            window.setEnabledPlayPauseButton(false);
            window.setPlayPauseButtonIcon(0);

            // desabilitando o stop
            window.setEnabledStopButton(false);

            // apagando as infos
            window.resetMiniPlayer();
        });

        paused = false;
        playing = false;

    }

    // começa a tocar a música
    private void startPlaying (int songIndex) {
        playing = true;
        currentSongIndex = songIndex;
        Song selected_song = song[songIndex];

        try {
            device = FactoryRegistry.systemRegistry().createAudioDevice();
            device.open(decoder = new Decoder());
            bitstream = new Bitstream(selected_song.getBufferedInputStream());
        }
        catch (JavaLayerException | FileNotFoundException ex) {
            throw new RuntimeException(ex);
        }

        currentFrame = 0;

        EventQueue.invokeLater(() -> {
            // habilitando play e pause
            window.setEnabledPlayPauseButton(true);
            window.setPlayPauseButtonIcon(1);

            // habilitando botão stop
            window.setEnabledStopButton(true);

            // infos da música
            window.setPlayingSongInfo(songInfo[songIndex][0], songInfo[songIndex][1], songInfo[songIndex][2]);

            // atualizando os botões próximo e anterior
            updatePreviousAndNextButtons();

            // ligando o scrubber
            window.setEnabledScrubber(true);
        });

        playThread = new Thread(() -> {

            // tempo da música
            int totalTime = (int) song[currentSongIndex].getMsLength();
            while (!playThread.currentThread().isInterrupted()) {

                try {
                    if (!paused) {
                        int currentTime = (int) (song[currentSongIndex].getMsPerFrame() * currentFrame);
                        if(!scrubberDragging) {

                            EventQueue.invokeLater(() -> {
                                window.setTime(currentTime, totalTime);
                            });
                        }

                        if (!playNextFrame()) {
                            Thread playNextThread = new Thread(() -> {
                                if (currentSongIndex < song.length - 1) {
                                    stopPlaying();
                                    startPlaying(currentSongIndex + 1);
                                }

                                // recomeçaremos a lista caso seja a última a tocar e o loop estiver ativo
                                else if (currentSongIndex == song.length - 1 && loop){
                                    stopPlaying();
                                    startPlaying(0);
                                }

                                else {
                                    stopPlaying();
                                }
                            });

                            playNextThread.start();
                            playNextThread.join();

                        }
                        currentFrame++;
                    }

                    else {
                        //currentFrame = pausedFrame;
                        //currentFrame++;
                    }

                } catch (JavaLayerException | InterruptedException ex){
                    throw new RuntimeException(ex);
                }
            }
        });
        playThread.start();
    }

    // retorna o index da música na lista
    private int getSelectedSongIndex(){

        for (int i = 0; i < songInfo.length; i++){
            String address = songInfo[i][5];
            if (Objects.equals(address, window.getSelectedSongID())){

                return i;
            }
        }
        return 0;
    }

    // deletando a música selecionada na lista
    private void deleteSong(int index){
        String songId = songInfo[index][5];

        // fazendo cópia dos arrays songs e songsInfo, excluindo o index desejado
        Song[] auxSong = new Song[song.length-1];
        String[][] auxSongInfo = new String[songInfo.length -1][];

        int contador = 0;

        for (int i = 0; i < song.length; i++){
            if(i != index){
                auxSong[contador] = song[i];
                auxSongInfo[contador] = songInfo[i];
                contador++;
            }
        }

        song = auxSong;
        songInfo = auxSongInfo;

        // mesma cópia para regularSong e regularSongInfo
        auxSong = new Song[regularSong.length - 1];
        auxSongInfo = new String[regularSongInfo.length - 1][];

        contador = 0;

        for (int i = 0; i < regularSongInfo.length; i++){
            if(!(regularSongInfo[i][5] == songId)){
                auxSong[contador] = regularSong[i];
                auxSongInfo[contador] = regularSongInfo[i];
                contador++;
            }
        }

        regularSong = auxSong;
        regularSongInfo = auxSongInfo;

        // atualizando o currentSongIndex
        if (index < currentSongIndex) {
            currentSongIndex--;
        }

        EventQueue.invokeLater( () -> {
            // atualizando a lista
            window.setQueueList(songInfo);
        });
    }

    private void updatePreviousAndNextButtons() {
        // atualizando os botões na interface
        if (song.length == 1){
            window.setEnabledPreviousButton(false);
            window.setEnabledNextButton(false);
        }

        else if (currentSongIndex == 0){
            window.setEnabledPreviousButton(false);
            window.setEnabledNextButton(true);
        }

        else if (currentSongIndex == song.length - 1){
            window.setEnabledPreviousButton(true);
            window.setEnabledNextButton(false);
        }

        else {
            window.setEnabledPreviousButton(true);
            window.setEnabledNextButton(true);
        }
    }

    private void shuffleSongs(){
        int index;
        Song tempSong;
        String[] tempInfo;
        Random random = new Random();

        for (int i = song.length - 1; i > 0; i--){
            index = random.nextInt(i + 1);

            tempSong = song[index];
            song[index] = song[i];
            song[i] = tempSong;

            tempInfo = songInfo[index];
            songInfo[index] = songInfo[i];
            songInfo[i] = tempInfo;

        }
    }
}

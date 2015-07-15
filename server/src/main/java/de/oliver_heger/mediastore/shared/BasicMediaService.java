package de.oliver_heger.mediastore.shared;

import com.google.gwt.user.client.rpc.RemoteService;
import com.google.gwt.user.client.rpc.RemoteServiceRelativePath;

import de.oliver_heger.mediastore.shared.model.AlbumDetailInfo;
import de.oliver_heger.mediastore.shared.model.ArtistDetailInfo;
import de.oliver_heger.mediastore.shared.model.SongDetailInfo;

/**
 * <p>
 * The service interface of the basic media service.
 * </p>
 * <p>
 * The <em>basic media service</em> provides a set of standard operations on the
 * media types supported. This includes querying detail information, removing
 * entities, handling synonyms, etc.
 * </p>
 *
 * @author Oliver Heger
 * @version $Id: $
 */
@RemoteServiceRelativePath("media")
public interface BasicMediaService extends RemoteService
{
    /**
     * Returns a data object with detail information for the specified artist.
     * This object contains all information available about this artist. It can
     * be used for instance to populate a details page.
     *
     * @param artistID the ID of the artist in question
     * @return a data object with details about this artist
     * @throws javax.persistence.EntityNotFoundException if the artist cannot be
     *         resolved
     * @throws IllegalStateException if the artist does not belong to the
     *         current user
     */
    ArtistDetailInfo fetchArtistDetails(long artistID);

    /**
     * Updates the synonyms of the specified artist. The passed in data object
     * contains information about the changes to be performed on the artist's
     * synonyms.
     *
     * @param artistID the ID of the artist in question
     * @param updateData an object with information about updates to be
     *        performed
     * @throws javax.persistence.EntityNotFoundException if the artist cannot be
     *         resolved
     * @throws IllegalStateException if the artist does not belong to the
     *         current user
     * @throws NullPointerException if the update data object is <b>null</b>
     */
    void updateArtistSynonyms(long artistID, SynonymUpdateData updateData);

    /**
     * Removes the artist with the specified ID. If there are songs referencing
     * this artist, this reference is cleared so that the songs do not belong to
     * any artist after the operation.
     *
     * @param artistID the ID of the artist to be removed
     * @return a flag whether the artist was removed successfully
     * @throws IllegalStateException if the artist does not belong to the
     *         current user
     */
    boolean removeArtist(long artistID);

    /**
     * Returns a data object with detail information for the specified song.
     * This object contains all information available about this song. It can be
     * used for instance to populate a details page.
     *
     * @param songID the ID of the song in question
     * @return a data object with details about this song
     * @throws javax.persistence.EntityNotFoundException if the song cannot be
     *         resolved
     * @throws IllegalStateException if the song does not belong to the current
     *         user
     */
    SongDetailInfo fetchSongDetails(String songID);

    /**
     * Updates the synonyms of the specified song. The passed in data object
     * contains information about the changes to be performed on the song's
     * synonyms.
     *
     * @param songID the ID of the song in question
     * @param updateData an object with information about updates to be
     *        performed
     * @throws javax.persistence.EntityNotFoundException if the song cannot be
     *         resolved
     * @throws IllegalStateException if the song does not belong to the current
     *         user
     * @throws NullPointerException if the update data object is <b>null</b>
     */
    void updateSongSynonyms(String songID, SynonymUpdateData updateData);

    /**
     * Removes the song with the specified ID. All dependent objects like
     * synonyms are removed, too.
     *
     * @param songID the ID of the song to be removed
     * @return a flag whether the song could be removed successfully
     * @throws IllegalStateException if the song does not belong to the current
     *         user
     */
    boolean removeSong(String songID);

    /**
     * Returns a data object with detail information for the specified album.
     * This object can be used to populate a detail page about this album.
     *
     * @param albumID the ID of the album in question
     * @return a data object with details about this album
     * @throws javax.persistence.EntityNotFoundException if the song cannot be
     *         resolved
     * @throws IllegalStateException if the song does not belong to the current
     *         user
     */
    AlbumDetailInfo fetchAlbumDetails(long albumID);

    /**
     * Updates the synonyms of the specified album. The passed in data object
     * contains information about the changes to be performed on the album's
     * synonyms.
     *
     * @param albumID the ID of the album in question
     * @param updateData an object with information about updates to be
     *        performed
     * @throws javax.persistence.EntityNotFoundException if the album cannot be
     *         resolved
     * @throws IllegalStateException if the album does not belong to the current
     *         user
     * @throws NullPointerException if the update data object is <b>null</b>
     */
    void updateAlbumSynonyms(long albumID, SynonymUpdateData updateData);

    /**
     * Removes the album with the specified ID. If there are songs referencing
     * this album, they do not point to any album after the operation.
     *
     * @param albumID the ID of the album to be removed
     * @return a flag whether the album was removed successfully
     * @throws IllegalStateException if the album does not belong to the current
     *         user
     */
    boolean removeAlbum(long albumID);
}

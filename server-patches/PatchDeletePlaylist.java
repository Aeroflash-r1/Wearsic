import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Rewrites Database.deletePlaylistTrack(pid,tid):
 *   tid.equals("*")  ->  DELETE the whole playlist row (FK cascades tracks)
 *   otherwise        ->  original single-track delete
 * Generated via ASM with COMPUTE_FRAMES (valid stack maps guaranteed).
 */
public class PatchDeletePlaylist implements Opcodes {

    static final String SINGLE_SQL =
        "DELETE FROM playlist_tracks WHERE playlist_id = ? AND video_id = ?";
    static final String WHOLE_SQL =
        "DELETE FROM playlists WHERE id = ?";

    public static void main(String[] args) throws Exception {
        Path in = Paths.get(args[0]);
        byte[] bytes = Files.readAllBytes(in);

        ClassReader cr = new ClassReader(bytes);
        ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_FRAMES);

        ClassVisitor cv = new ClassVisitor(ASM9, cw) {
            @Override
            public MethodVisitor visitMethod(int access, String name, String desc,
                                             String signature, String[] exceptions) {
                if ("deletePlaylistTrack".equals(name)) {
                    // Preserve original access modifiers (public synchronized final)
                    MethodVisitor mv = cv.visitMethod(
                            ACC_PUBLIC | ACC_SYNCHRONIZED | ACC_FINAL,
                            name,
                            "(Ljava/lang/String;Ljava/lang/String;)Z",
                            null,
                            exceptions != null ? exceptions : new String[]{"java/lang/Exception"});
                    emitBody(mv);
                    return null; // we've written the full method
                }
                return super.visitMethod(access, name, desc, signature, exceptions);
            }
        };

        cr.accept(cv, 0);
        Files.write(in, cw.toByteArray());
        System.out.println("PATCHED deletePlaylistTrack with wildcard support");
    }

    private static void emitBody(MethodVisitor mv) {
        Label tryStart = new Label();
        Label afterBody = new Label();
        Label catchHandler = new Label();
        Label isWildcard = new Label();
        Label singleDone = new Label();
        Label wildcardDone = new Label();
        Label merge = new Label();

        mv.visitCode();

        // synchronized (this) {
        mv.visitVarInsn(ALOAD, 0);
        mv.visitInsn(MONITORENTER);
        mv.visitLabel(tryStart);

        // Connection conn = this.connection;
        mv.visitVarInsn(ALOAD, 0);
        mv.visitFieldInsn(GETFIELD, "com/wearsic/server/Database",
                "connection", "Ljava/sql/Connection;");
        mv.visitVarInsn(ASTORE, 3); // conn

        // if (!tid.equals("*")) goto single path
        mv.visitVarInsn(ALOAD, 2);
        mv.visitLdcInsn("*");
        mv.visitMethodInsn(INVOKEVIRTUAL, "java/lang/String",
                "equals", "(Ljava/lang/Object;)Z", false);
        mv.visitJumpInsn(IFNE, isWildcard);

        // ---- single track delete ----
        mv.visitVarInsn(ALOAD, 3);
        mv.visitLdcInsn(SINGLE_SQL);
        mv.visitMethodInsn(INVOKEINTERFACE, "java/sql/Connection",
                "prepareStatement", "(Ljava/lang/String;)Ljava/sql/PreparedStatement;", true);
        mv.visitVarInsn(ASTORE, 4); // ps
        mv.visitVarInsn(ALOAD, 4);
        mv.visitInsn(ICONST_1);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitMethodInsn(INVOKEINTERFACE, "java/sql/PreparedStatement",
                "setString", "(ILjava/lang/String;)V", true);
        mv.visitVarInsn(ALOAD, 4);
        mv.visitInsn(ICONST_2);
        mv.visitVarInsn(ALOAD, 2);
        mv.visitMethodInsn(INVOKEINTERFACE, "java/sql/PreparedStatement",
                "setString", "(ILjava/lang/String;)V", true);
        mv.visitVarInsn(ALOAD, 4);
        mv.visitMethodInsn(INVOKEINTERFACE, "java/sql/PreparedStatement",
                "executeUpdate", "()I", true);
        mv.visitVarInsn(ISTORE, 5); // rows
        mv.visitVarInsn(ALOAD, 4);
        mv.visitMethodInsn(INVOKEINTERFACE, "java/sql/PreparedStatement",
                "close", "()V", true);
        mv.visitJumpInsn(GOTO, singleDone);

        // ---- whole playlist delete (FK cascade wipes tracks) ----
        mv.visitLabel(isWildcard);
        mv.visitVarInsn(ALOAD, 3);
        mv.visitLdcInsn(WHOLE_SQL);
        mv.visitMethodInsn(INVOKEINTERFACE, "java/sql/Connection",
                "prepareStatement", "(Ljava/lang/String;)Ljava/sql/PreparedStatement;", true);
        mv.visitVarInsn(ASTORE, 4);
        mv.visitVarInsn(ALOAD, 4);
        mv.visitInsn(ICONST_1);
        mv.visitVarInsn(ALOAD, 1);
        mv.visitMethodInsn(INVOKEINTERFACE, "java/sql/PreparedStatement",
                "setString", "(ILjava/lang/String;)V", true);
        mv.visitVarInsn(ALOAD, 4);
        mv.visitMethodInsn(INVOKEINTERFACE, "java/sql/PreparedStatement",
                "executeUpdate", "()I", true);
        mv.visitVarInsn(ISTORE, 5);
        mv.visitVarInsn(ALOAD, 4);
        mv.visitMethodInsn(INVOKEINTERFACE, "java/sql/PreparedStatement",
                "close", "()V", true);

        // ---- merge: result = rows > 0 ----
        mv.visitLabel(singleDone);
        mv.visitVarInsn(ILOAD, 5);
        mv.visitJumpInsn(IFLE, wildcardDone);
        mv.visitInsn(ICONST_1);
        mv.visitJumpInsn(GOTO, merge);
        mv.visitLabel(wildcardDone);
        mv.visitInsn(ICONST_0);

        mv.visitLabel(merge);
        mv.visitVarInsn(ISTORE, 6); // boolean result

        mv.visitVarInsn(ALOAD, 0);
        mv.visitInsn(MONITOREXIT);
        mv.visitLabel(afterBody);
        mv.visitVarInsn(ILOAD, 6);
        mv.visitInsn(IRETURN);

        mv.visitLabel(catchHandler);
        mv.visitVarInsn(ALOAD, 0);
        mv.visitInsn(MONITOREXIT);
        mv.visitInsn(ATHROW);

        mv.visitTryCatchBlock(tryStart, afterBody, catchHandler, "java/lang/Throwable");

        mv.visitMaxs(0, 0); // COMPUTE_FRAMES recalculates everything
        mv.visitEnd();
    }
}